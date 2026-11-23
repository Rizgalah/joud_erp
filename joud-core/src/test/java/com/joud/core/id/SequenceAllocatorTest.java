package com.joud.core.id;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SequenceAllocatorTest {

    @Mock
    private EntityManager em;

    private Query nativeQuery(String sqlPrefix) {
        Query q = mock(Query.class);
        lenient().when(q.setParameter(anyInt(), any())).thenReturn(q);
        lenient().when(q.executeUpdate()).thenReturn(1);
        lenient().when(em.createNativeQuery(startsWith(sqlPrefix))).thenReturn(q);
        return q;
    }

    private void givenMaxIdInTable(long maxId) {
        Query q = nativeQuery("SELECT COALESCE");
        lenient().when(q.getSingleResult()).thenReturn(new BigDecimal(maxId));
    }

    private Query givenRegistryRow(List<?> rows) {
        Query q = nativeQuery("SELECT NEXT_VAL");
        lenient().when(q.getResultList()).thenReturn((List) rows);
        return q;
    }

    @Test
    @DisplayName("لا يوجد صف في السجل: يبدأ من أكبر معرّف + 1 ويُدرج صفاً جديداً")
    void noRegistryRowInsertsFromMaxId() {
        givenMaxIdInTable(41);
        givenRegistryRow(List.of());
        Query insert = nativeQuery("INSERT INTO JOUD_SEQUENCE_REGISTRY");

        long id = SequenceAllocator.nextId(em, "USERS", "users", "user_i_id");

        assertEquals(42L, id);
        verify(insert).setParameter(1, "USERS");
        verify(insert).setParameter(2, 42L);
        verify(insert).executeUpdate();
    }

    @Test
    @DisplayName("صف السجل أكبر من أكبر معرّف: يتابع من قيمة السجل ويُحدّثها")
    void registryAheadOfTable() {
        givenMaxIdInTable(10);
        givenRegistryRow(List.of(new BigDecimal(100)));
        Query update = nativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY");

        long id = SequenceAllocator.nextId(em, "USERS", "users", "user_i_id");

        assertEquals(101L, id);
        verify(update).setParameter(1, 101L);
        verify(update).setParameter(2, "USERS");
        verify(update).executeUpdate();
    }

    @Test
    @DisplayName("الجدول يحوي معرّفات أكبر من السجل (إدخال يدوي): لا تصادم")
    void tableAheadOfRegistry() {
        givenMaxIdInTable(500);
        givenRegistryRow(List.of(new BigDecimal(3)));
        nativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY");

        assertEquals(501L, SequenceAllocator.nextId(em, "USERS", "users", "user_i_id"));
    }

    @Test
    @DisplayName("عند وجود صف في السجل لا يُدرج صفاً جديداً")
    void existingRowIsNotReinserted() {
        givenMaxIdInTable(0);
        givenRegistryRow(List.of(new BigDecimal(7)));
        nativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY");

        SequenceAllocator.nextId(em, "ROLE", "role", "role_i_id");

        verify(em, never()).createNativeQuery(startsWith("INSERT INTO JOUD_SEQUENCE_REGISTRY"));
    }

    @Test
    @DisplayName("nextValue: لا صف في السجل → floor + 1 ويُدرج صفاً")
    void nextValueWithoutRegistryRow() {
        givenRegistryRow(List.of());
        Query insert = nativeQuery("INSERT INTO JOUD_SEQUENCE_REGISTRY");

        assertEquals(6L, SequenceAllocator.nextValue(em, "JV_REF_2026", 5));
        verify(insert).setParameter(1, "JV_REF_2026");
        verify(insert).setParameter(2, 6L);
    }

    @Test
    @DisplayName("nextValue: يأخذ الأكبر بين السجل وfloor")
    void nextValueUsesLargerOfRegistryAndFloor() {
        givenRegistryRow(List.of(new BigDecimal(3)));
        nativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY");
        assertEquals(51L, SequenceAllocator.nextValue(em, "JV_REF_2026", 50));

        givenRegistryRow(List.of(new BigDecimal(80)));
        assertEquals(81L, SequenceAllocator.nextValue(em, "JV_REF_2026", 50));
    }
}
