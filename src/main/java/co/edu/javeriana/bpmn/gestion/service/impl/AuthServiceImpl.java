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

/**
 * Implementación del inicio de sesión (HU-03): comprueba las credenciales con Spring Security
 * y entrega un token JWT para que el cliente lo envíe en las siguientes peticiones.
 */
// @Service: Spring crea una instancia y la deja disponible para inyectarla.
// @RequiredArgsConstructor (Lombok): genera el constructor con los campos final, y así Spring los inyecta.
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Esquema del token que se devuelve al cliente; se envía luego como "Authorization: Bearer ...". */
    private static final String TIPO_TOKEN = "Bearer";

    /** Componente de Spring Security que verifica correo y contraseña. */
    private final AuthenticationManager authenticationManager;
    /** Servicio propio que genera el token JWT. */
    private final JwtService jwtService;
    /** Acceso a la tabla de usuarios. */
    private final UsuarioRepository usuarioRepository;
    /** Se usa para traer los datos de la empresa del usuario. */
    private final EmpresaService empresaService;

    /**
     * Valida las credenciales y devuelve el token con el resumen del usuario.
     * readOnly = true porque aquí solo se lee de la base de datos, no se modifica nada.
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Paso 1: normaliza el correo (sin espacios y en minúsculas) para que "Ana@x.com " y "ana@x.com" sean iguales.
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);

        // Paso 2: Spring Security comprueba la contraseña contra el hash guardado.
        // Falla con AuthenticationException si el correo no existe, la clave no coincide o la cuenta está inactiva
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(correo, request.password()));

        // Paso 3: carga el usuario ya autenticado (el orElseThrow es una salvaguarda que casi nunca ocurre).
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Correo o contraseña incorrectos"));

        // Paso 4: genera el JWT y arma la respuesta con el usuario y su empresa.
        return new LoginResponse(jwtService.generarToken(usuario), TIPO_TOKEN, jwtService.getExpiracionSegundos(),
                UsuarioResumen.de(usuario, empresaService.obtenerEntidad(usuario.getEmpresaId())));
    }

    /**
     * Devuelve el usuario autenticado y su empresa. Busca el usuario filtrando por empresa:
     * si no es de esa empresa responde 404.
     */
    @Override
    @Transactional(readOnly = true)
    public UsuarioResumen yo(Long usuarioId, Long empresaId) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new NotFoundException("No existe el usuario " + usuarioId));
        return UsuarioResumen.de(usuario, empresaService.obtenerEntidad(empresaId));
    }
}
