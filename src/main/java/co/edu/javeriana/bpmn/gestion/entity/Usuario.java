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
@Entity
@Table(name = "usuarios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_correo", columnNames = "correo"),
        indexes = @Index(name = "idx_usuario_empresa", columnList = "empresa_id"))
@Getter
@Setter
@ToString(exclude = "password")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends EntidadDeEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120, nullable = false)
    private String nombre;

    @Column(length = 120, nullable = false)
    private String correo;

    /** Hash BCrypt (60 caracteres); nunca la contraseña en texto plano. */
    @Column(length = 100, nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RolAcceso rol;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
