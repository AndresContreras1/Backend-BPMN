package co.edu.javeriana.bpmn.gestion.service.impl;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.common.exception.ReglaDeNegocioException;
import co.edu.javeriana.bpmn.gestion.dto.EmpresaResponse;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaRequest;
import co.edu.javeriana.bpmn.gestion.dto.RegistroEmpresaResponse;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResponse;
import co.edu.javeriana.bpmn.gestion.entity.Empresa;
import co.edu.javeriana.bpmn.gestion.entity.RolAcceso;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.EmpresaRepository;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.EmpresaService;
import lombok.RequiredArgsConstructor;

/** Implementación del servicio de empresas: registra empresas nuevas y consulta las existentes. */
// @Service: bean de Spring. @RequiredArgsConstructor: constructor con los campos final para la inyección.
@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements EmpresaService {

    /** Acceso a la tabla de empresas. */
    private final EmpresaRepository empresaRepository;
    /** Acceso a la tabla de usuarios (para crear el administrador inicial). */
    private final UsuarioRepository usuarioRepository;
    /** Cifra las contraseñas (BCrypt) antes de guardarlas. */
    private final PasswordEncoder passwordEncoder;

    /**
     * HU-01: crea la empresa y su administrador inicial en una sola transacción: si algo falla,
     * no queda guardada ni la empresa ni el usuario.
     */
    @Override
    @Transactional
    public RegistroEmpresaResponse registrar(RegistroEmpresaRequest request) {
        // Paso 1: limpia los datos de entrada (NIT sin espacios; correo sin espacios y en minúsculas).
        String nit = request.nit().trim();
        String correo = request.correoContacto().trim().toLowerCase(Locale.ROOT);

        // Paso 2: reglas de negocio, el NIT y el correo no pueden estar ya registrados.
        if (empresaRepository.existsByNitIgnoreCase(nit)) {
            throw new ReglaDeNegocioException("Ya existe una empresa registrada con el NIT " + nit);
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ReglaDeNegocioException("El correo " + correo + " ya está registrado como usuario");
        }

        // Paso 3: guarda la empresa.
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre(request.nombre().trim())
                .nit(nit)
                .correoContacto(correo)
                .build());

        // Paso 4: crea el usuario administrador de esa empresa; la contraseña se guarda cifrada (hash).
        Usuario administrador = Usuario.builder()
                .nombre(request.nombreRepresentante().trim())
                .correo(correo)
                .password(passwordEncoder.encode(request.password()))
                .rol(RolAcceso.ADMINISTRADOR)
                .build();
        administrador.setEmpresaId(empresa.getId());   // lo pone el servidor, no el cliente

        // Paso 5: guarda al administrador y devuelve ambos como DTO (sin contraseña).
        return new RegistroEmpresaResponse(EmpresaResponse.de(empresa),
                UsuarioResponse.de(usuarioRepository.save(administrador)));
    }

    /** Devuelve los datos de una empresa activa como DTO (solo lectura). */
    @Override
    @Transactional(readOnly = true)
    public EmpresaResponse obtener(Long empresaId) {
        return EmpresaResponse.de(obtenerEntidad(empresaId));
    }

    /**
     * Permite consultar solo la empresa del propio usuario. Si el id pedido no es el de su empresa,
     * responde 404 (y no 403) para no revelar que esa otra empresa existe.
     */
    @Override
    @Transactional(readOnly = true)
    public EmpresaResponse obtenerPropia(Long id, Long empresaId) {
        if (!id.equals(empresaId)) {
            throw new NotFoundException("No existe la empresa " + id);
        }
        return obtener(empresaId);
    }

    /** Busca la entidad Empresa activa; si no existe lanza NotFoundException (404). */
    @Override
    @Transactional(readOnly = true)
    public Empresa obtenerEntidad(Long empresaId) {
        return empresaRepository.findByIdAndActivoTrue(empresaId)
                .orElseThrow(() -> new NotFoundException("No existe la empresa " + empresaId));
    }
}
