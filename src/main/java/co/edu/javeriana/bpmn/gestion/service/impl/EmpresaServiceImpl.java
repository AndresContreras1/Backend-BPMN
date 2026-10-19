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

@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegistroEmpresaResponse registrar(RegistroEmpresaRequest request) {
        String nit = request.nit().trim();
        String correo = request.correoContacto().trim().toLowerCase(Locale.ROOT);

        if (empresaRepository.existsByNitIgnoreCase(nit)) {
            throw new ReglaDeNegocioException("Ya existe una empresa registrada con el NIT " + nit);
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ReglaDeNegocioException("El correo " + correo + " ya está registrado como usuario");
        }

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre(request.nombre().trim())
                .nit(nit)
                .correoContacto(correo)
                .build());

        Usuario administrador = Usuario.builder()
                .nombre(request.nombreRepresentante().trim())
                .correo(correo)
                .password(passwordEncoder.encode(request.password()))
                .rol(RolAcceso.ADMINISTRADOR)
                .build();
        administrador.setEmpresaId(empresa.getId());   // lo pone el servidor, no el cliente

        return new RegistroEmpresaResponse(EmpresaResponse.de(empresa),
                UsuarioResponse.de(usuarioRepository.save(administrador)));
    }

    @Override
    @Transactional(readOnly = true)
    public EmpresaResponse obtener(Long empresaId) {
        return EmpresaResponse.de(obtenerEntidad(empresaId));
    }

    @Override
    @Transactional(readOnly = true)
    public EmpresaResponse obtenerPropia(Long id, Long empresaId) {
        if (!id.equals(empresaId)) {
            throw new NotFoundException("No existe la empresa " + id);
        }
        return obtener(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public Empresa obtenerEntidad(Long empresaId) {
        return empresaRepository.findByIdAndActivoTrue(empresaId)
                .orElseThrow(() -> new NotFoundException("No existe la empresa " + empresaId));
    }
}
