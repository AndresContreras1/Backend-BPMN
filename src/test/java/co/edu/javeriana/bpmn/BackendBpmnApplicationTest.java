package co.edu.javeriana.bpmn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendBpmnApplicationTest {

    @Test
    void BackendBpmnApplication_contextLoads_SinErrores() {
        // Si el contexto levanta, la configuracion de perfiles y JPA es valida
    }
}
