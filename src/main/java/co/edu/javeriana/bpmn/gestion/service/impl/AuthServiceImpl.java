package co.edu.javeriana.bpmn.gestion.service.impl;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.bpmn.common.exception.NotFoundException;
import co.edu.javeriana.bpmn.gestion.dto.LoginRequest;
import co.edu.javeriana.bpmn.gestion.dto.LoginResponse;
import co.edu.javeriana.bpmn.gestion.dto.UsuarioResumen;
import co.edu.javeriana.bpmn.gestion.entity.Usuario;
import co.edu.javeriana.bpmn.gestion.repository.UsuarioRepository;
import co.edu.javeriana.bpmn.gestion.service.AuthService;
import co.edu.javeriana.bpmn.gestion.service.EmpresaService;
import co.edu.javeriana.bpmn.security.JwtService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TIPO_TOKEN = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);

        // Falla con AuthenticationException si el correo no existe, la clave no coincide o la cuenta está inactiva
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(correo, request.password()));

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Correo o contraseña incorrectos"));

        return new LoginResponse(jwtService.generarToken(usuario), TIPO_TOKEN, jwtService.getExpiracionSegundos(),
                UsuarioResumen.de(usuario, empresaService.obtenerEntidad(usuario.getEmpresaId())));
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResumen yo(Long usuarioId, Long empresaId) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el usuario " + usuarioId));
        return UsuarioResumen.de(usuario, empresaService.obtenerEntidad(empresaId));
    }
}
