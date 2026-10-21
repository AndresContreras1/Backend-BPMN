package co.edu.javeriana.bpmn.gestion.entity;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Cuenta de un colaborador de la empresa (HU-02). El correo es el usuario de inicio de sesión
 * (HU-03) y la contraseña se guarda solo como hash BCrypt. Desactivar no borra: el usuario
 * conserva su historial y los procesos siguen siendo de la empresa.
 */
// Tabla "usuarios": el correo es único y hay un índice por empresa para filtrar rápido los usuarios de cada una.
@Entity
@Table(name = "usuarios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_correo", columnNames = "correo"),
        indexes = @Index(name = "idx_usuario_empresa", columnList = "empresa_id"))
// Lombok genera getters, setters, constructores y builder; el toString excluye la contraseña para no filtrarla en logs.
@Getter
@Setter
@ToString(exclude = "password")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends EntidadDeEmpresa {

    /** Identificador del usuario, autoincremental en la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre completo del colaborador. */
    @Column(length = 120, nullable = false)
    private String nombre;

    /** Correo del usuario; se usa para iniciar sesión y es único en todo el sistema. */
    @Column(length = 120, nullable = false)
    private String correo;

    /** Hash BCrypt (60 caracteres); nunca la contraseña en texto plano. */
    @Column(length = 100, nullable = false)
    private String password;

    /** Rol de acceso (ADMINISTRADOR, EDITOR o LECTOR); se guarda como texto, no como número. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RolAcceso rol;

    /** false cuando el usuario está desactivado (no puede entrar, pero no se borra). */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
