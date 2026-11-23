package com.joud.core.security.service;

import com.joud.core.db.JoudRepository;
import com.joud.core.id.SequenceAllocator;
import com.joud.core.security.model.Role;
import com.joud.core.security.model.User;
import com.joud.core.security.util.PasswordUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * UserService — إدارة المستخدمين (CRUD)
 *
 * إنشاء مستخدم، تغيير كلمة المرور، إدارة الأدوار،
 * قفل/فتح الحساب، البحث.
 *
 * الحفظ عبر JoudRepository (معاملات يدوية) لأن Tomcat لا يوفر JTA.
 */
@ApplicationScoped
public class UserService {

    // أسماء التسلسل بنفس قاعدة JoudIdGenerator: اسم الجدول بحروف كبيرة
    private static final String USERS_SEQ = "USERS";
    private static final String ROLE_SEQ = "ROLE";

    @Inject
    private JoudRepository db;

    // ─── إنشاء مستخدم جديد ──────────────────────────

    public User createUser(String username, String email, String plainPassword, Long agentId) {
        final String hash = PasswordUtil.hash(plainPassword); // بطيء، لذلك خارج المعاملة
        final String normalizedName = username.trim().toLowerCase();
        final String normalizedEmail = email.trim().toLowerCase();

        return db.executeInTransaction(em -> {
            User user = new User();
            user.setId(SequenceAllocator.nextId(em, USERS_SEQ, "users", "user_i_id"));
            user.setUsername(normalizedName);
            user.setEmail(normalizedEmail);
            user.setPasswordHash(hash);
            user.setAgentId(agentId);
            user.setStatus("A");
            em.persist(user);
            return user;
        });
    }

    // ─── تغيير كلمة المرور ──────────────────────────

    public void changePassword(Long userId, String newPlainPassword) {
        final String hash = PasswordUtil.hash(newPlainPassword);

        db.executeInTransaction(em -> {
            User user = em.find(User.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            user.setPasswordHash(hash);
            user.setFailedAttempts(0);
            // نفك القفل فقط، ولا نُعيد تفعيل حساب مُعطَّل (I)
            if ("L".equals(user.getStatus())) {
                user.setStatus("A");
            }
        });
    }

    // ─── إضافة دور لمستخدم ──────────────────────────

    public void assignRole(Long userId, Long roleId) {
        db.executeInTransaction(em -> {
            User user = em.find(User.class, userId);
            Role role = em.find(Role.class, roleId);
            if (user == null || role == null) {
                return;
            }
            user.getRoles().add(role);
        });
    }

    // ─── قفل / فتح حساب ─────────────────────────────

    public void lockUser(Long userId) {
        db.executeInTransaction(em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setStatus("L");
            }
        });
    }

    public void unlockUser(Long userId) {
        db.executeInTransaction(em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setStatus("A");
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }
        });
    }

    // ─── البحث ──────────────────────────────────────

    public Optional<User> findById(Long id) {
        return db.execute(em -> Optional.ofNullable(em.find(User.class, id)));
    }

    public List<User> findByAgent(Long agentId) {
        return db.fetchAll("SELECT u FROM User u WHERE u.agentId = ?1 ORDER BY u.username",
                User.class, agentId);
    }

    public List<User> findAll() {
        return db.fetchAll("SELECT u FROM User u ORDER BY u.username", User.class);
    }

    // ─── الأدوار ─────────────────────────────────────

    public Role createRole(String name, String description) {
        return db.executeInTransaction(em -> {
            Role role = new Role();
            role.setId(SequenceAllocator.nextId(em, ROLE_SEQ, "role", "role_i_id"));
            role.setName(name);
            role.setDescription(description);
            em.persist(role);
            return role;
        });
    }

    public List<Role> findAllRoles() {
        return db.fetchAll("SELECT r FROM Role r ORDER BY r.name", Role.class);
    }
}
