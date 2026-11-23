package cl.duoc.veterinaria.config;

import cl.duoc.veterinaria.model.Role;
import cl.duoc.veterinaria.model.User;
import cl.duoc.veterinaria.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea los usuarios de demostración al arrancar, solo si todavía no existen:
 *   admin@sanmarcos.cl / Admin123!      (ADMIN)
 *   cliente@sanmarcos.cl / Cliente123!  (CLIENTE)
 * El correo y la contraseña del admin se pueden cambiar con APP_ADMIN_EMAIL y APP_ADMIN_PASSWORD.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean demoClient;

    public DataInitializer(UserRepository users, PasswordEncoder encoder,
                           @Value("${app.seed.admin-email}") String adminEmail,
                           @Value("${app.seed.admin-password}") String adminPassword,
                           @Value("${app.seed.demo-client}") boolean demoClient) {
        this.users = users;
        this.encoder = encoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demoClient = demoClient;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("Administración San Marcos", adminEmail, adminPassword, Role.ADMIN);
        if (demoClient) {
            createIfMissing("Vendedor Demo", "vendedor@sanmarcos.cl", "Vendedor123!", Role.VENDEDOR);
            createIfMissing("Cliente Demo", "cliente@sanmarcos.cl", "Cliente123!", Role.CLIENTE);
        }
    }

    private void createIfMissing(String name, String email, String password, Role role) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new User(name, email.toLowerCase(), encoder.encode(password), role));
            log.info("Usuario inicial creado: {} ({})", email, role);
        }
    }
}
