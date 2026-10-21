package co.edu.javeriana.bpmn.arquitectura;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import co.edu.javeriana.bpmn.common.entity.EntidadDeEmpresa;
import jakarta.persistence.Entity;

/**
 * Reglas de arquitectura: capas, nombres, inyección por constructor, multiempresa y seguridad.
 */
// ArchUnit lee las clases compiladas del proyecto (sin las de prueba) y cada campo @ArchTest es una regla que debe cumplirse.
@AnalyzeClasses(packages = "co.edu.javeriana.bpmn", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    // Regla: lo que está en repository son interfaces de Spring Data cuyo nombre termina en Repository.
    @ArchTest
    static final ArchRule los_repositorios_son_interfaces_de_spring_data = classes()
            .that().resideInAPackage("..repository..")
            .should().beInterfaces()
            .andShould().beAssignableTo(JpaRepository.class)
            .andShould().haveSimpleNameEndingWith("Repository");

    // Regla: los repositorios no usan servicios ni controladores (dependencias solo hacia abajo).
    @ArchTest
    static final ArchRule los_repositorios_no_dependen_de_capas_superiores = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
            .because("el repositorio solo consulta; puede devolver proyecciones JPQL a DTO");

    // Regla: las entidades no dependen de repositorios, servicios, controladores ni DTO.
    @ArchTest
    static final ArchRule las_entidades_no_dependen_de_otras_capas = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..repository..", "..service..", "..controller..", "..dto..");

    // Regla: toda clase con @Entity está en el paquete entity.
    @ArchTest
    static final ArchRule las_entidades_viven_en_el_paquete_entity = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..entity..");

    // Regla: toda entidad, salvo Empresa, hereda de EntidadDeEmpresa (es decir, guarda a qué empresa pertenece).
    @ArchTest
    static final ArchRule toda_entidad_de_negocio_es_multiempresa = classes()
            .that().areAnnotatedWith(Entity.class)
            .and().doNotHaveSimpleName("Empresa")
            .should().beAssignableTo(EntidadDeEmpresa.class)
            .because("todo dato del sistema cuelga de una empresa (HU-01)");

    // Regla: los controladores hablan con servicios, no con repositorios.
    @ArchTest
    static final ArchRule los_controladores_no_usan_repositorios = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("Controller -> Service -> Repository: no se saltan capas");

    // Regla: los controladores no manejan entidades, solo DTO.
    @ArchTest
    static final ArchRule las_entidades_no_salen_por_la_api = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("los controladores solo reciben y devuelven DTOs");

    // Regla: los servicios no dependen de controladores.
    @ArchTest
    static final ArchRule los_servicios_no_dependen_de_controladores = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    // Regla: las clases @RestController están en controller y su nombre termina en Controller.
    @ArchTest
    static final ArchRule los_controladores_rest_viven_en_controller = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..")
            .andShould().haveSimpleNameEndingWith("Controller");

    // Regla: los @Service son implementaciones: nombre ServiceImpl y paquete service.impl.
    @ArchTest
    static final ArchRule los_servicios_son_implementaciones = classes()
            .that().areAnnotatedWith(Service.class).and().resideInAPackage("..service..")
            .should().haveSimpleNameEndingWith("ServiceImpl")
            .andShould().resideInAPackage("..service.impl..");

    // Regla: ningún campo usa @Autowired; se inyecta por constructor.
    @ArchTest
    static final ArchRule sin_inyeccion_por_atributo = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("el proyecto usa inyección por constructor en todas las clases");

    // Regla: nadie accede a System.out ni System.err; se usa el Logger.
    @ArchTest
    static final ArchRule nada_de_System_out = noClasses()
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("los mensajes van al Logger, no a la consola");

    // Regla: dentro de security solo el UserDetailsService puede usar repositorios.
    @ArchTest
    static final ArchRule la_seguridad_solo_consulta_el_repositorio_desde_el_UserDetailsService = noClasses()
            .that().resideInAPackage("..security..")
            .and().doNotHaveSimpleName("CustomUserDetailService")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("el único puente entre Spring Security y la base de datos es el UserDetailsService");

    // Regla: los servicios no llaman findById, findAll ni getReferenceById de un repositorio, porque no filtran por empresa (evita acceder a datos ajenos, IDOR).
    @ArchTest
    static final ArchRule los_servicios_no_consultan_sin_filtrar_por_empresa = noClasses()
            .that().resideInAPackage("..service..")
            .should().callMethodWhere(target(name("findById").or(name("findAll")).or(name("getReferenceById")))
                    .and(target(owner(assignableTo(JpaRepository.class)))))
            .because("findById y findAll no filtran por empresa: los servicios usan las consultas ...EmpresaId (IDOR)");
}
