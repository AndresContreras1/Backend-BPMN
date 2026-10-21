package co.edu.javeriana.bpmn.arquitectura;

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
 * Reglas de arquitectura: capas, nombres, inyección por constructor y multiempresa.
 */
@AnalyzeClasses(packages = "co.edu.javeriana.bpmn", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule los_repositorios_son_interfaces_de_spring_data = classes()
            .that().resideInAPackage("..repository..")
            .should().beInterfaces()
            .andShould().beAssignableTo(JpaRepository.class)
            .andShould().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule los_repositorios_no_dependen_de_capas_superiores = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
            .because("el repositorio solo consulta; puede devolver proyecciones JPQL a DTO");

    @ArchTest
    static final ArchRule las_entidades_no_dependen_de_otras_capas = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..repository..", "..service..", "..controller..", "..dto..");

    @ArchTest
    static final ArchRule las_entidades_viven_en_el_paquete_entity = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule toda_entidad_de_negocio_es_multiempresa = classes()
            .that().areAnnotatedWith(Entity.class)
            .and().doNotHaveSimpleName("Empresa")
            .should().beAssignableTo(EntidadDeEmpresa.class)
            .because("todo dato del sistema cuelga de una empresa (HU-01)");

    @ArchTest
    static final ArchRule los_controladores_no_usan_repositorios = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("Controller -> Service -> Repository: no se saltan capas");

    @ArchTest
    static final ArchRule las_entidades_no_salen_por_la_api = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("los controladores solo reciben y devuelven DTOs");

    @ArchTest
    static final ArchRule los_servicios_no_dependen_de_controladores = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule los_controladores_rest_viven_en_controller = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..")
            .andShould().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule los_servicios_son_implementaciones = classes()
            .that().areAnnotatedWith(Service.class).and().resideInAPackage("..service..")
            .should().haveSimpleNameEndingWith("ServiceImpl")
            .andShould().resideInAPackage("..service.impl..");

    @ArchTest
    static final ArchRule sin_inyeccion_por_atributo = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("el proyecto usa inyección por constructor en todas las clases");
}
