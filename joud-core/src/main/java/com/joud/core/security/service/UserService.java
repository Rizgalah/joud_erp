package com.joud.core.security.service;

import com.joud.core.security.model.Role;
import com.joud.core.security.model.User;
import com.joud.core.security.util.PasswordUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * UserService — إدارة المستخدمين (CRUD)
 *
 * إنشاء مستخدم، تغيير كلمة المرور، إدارة الأدوار،
 * قفل/فتح الحساب، البحث.
 */
@ApplicationScoped
public class UserService {

    @PersistenceContext
    private EntityManager em;

    // ─── إنشاء مستخدم جديد ──────────────────────────

    @Transactional
    public User createUser(String username, String email, String plainPassword, Long agentId) {
        User user = new User();
        user.setId(generateId());
        user.setUsername(username.trim().toLowerCase());
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(PasswordUtil.hash(plainPassword));
        user.setAgentId(agentId);
        user.setStatus("A");
        em.persist(user);
        return user;
    }

    // ─── تغيير كلمة المرور ──────────────────────────

    @Transactional
    public void changePassword(Long userId, String newPlainPassword) {
        User user = em.find(User.class, userId);
        if (user == null)
            throw new IllegalArgumentException("User not found: " + userId);
        user.setPasswordHash(PasswordUtil.hash(newPlainPassword));
        user.setFailedAttempts(0);
        user.setStatus("A");
        em.merge(user);
    }

    // ─── إضافة دور لمستخدم ──────────────────────────

    @Transactional
    public void assignRole(Long userId, Long roleId) {
        User user = em.find(User.class, userId);
        Role role = em.find(Role.class, roleId);
        if (user == null || role == null)
            return;
        user.getRoles().add(role);
        em.merge(user);
    }

    // ─── قفل / فتح حساب ─────────────────────────────

    @Transactional
    public void lockUser(Long userId) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setStatus("L");
            em.merge(user);
        }
    }

    @Transactional
    public void unlockUser(Long userId) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setStatus("A");
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            em.merge(user);
        }
    }

    // ─── البحث ──────────────────────────────────────

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(em.find(User.class, id));
    }

    public List<User> findByAgent(Long agentId) {
        return em.createQuery(
                "SELECT u FROM User u WHERE u.agentId = :agentId ORDER BY u.username",
                User.class).setParameter("agentId", agentId).getResultList();
    }

    public List<User> findAll() {
        return em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class)
                .getResultList();
    }

    // ─── الأدوار ─────────────────────────────────────

    @Transactional
    public Role createRole(String name, String description) {
        Role role = new Role();
        role.setId(generateRoleId());
        role.setName(name);
        role.setDescription(description);
        em.persist(role);
        return role;
    }

    public List<Role> findAllRoles() {
        return em.createQuery("SELECT r FROM Role r ORDER BY r.name", Role.class)
                .getResultList();
    }

    // ─── Private ────────────────────────────────────

    private Long generateId() {
        try {
            return em.createQuery(
                    "SELECT k.next_val FROM joud_sequence_registry k WHERE k.seq_name = 'users'",
                    Long.class).getSingleResult();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private Long generateRoleId() {
        try {
            return em.createQuery(
                    "SELECT k.next_val FROM joud_sequence_registry k WHERE k.seq_name = 'role'",
                    Long.class).getSingleResult();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
