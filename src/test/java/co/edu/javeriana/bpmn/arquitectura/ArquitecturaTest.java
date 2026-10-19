package co.edu.javeriana.bpmn.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

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
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..dto..");

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
    static final ArchRule sin_inyeccion_por_atributo = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("el proyecto usa inyección por constructor en todas las clases");
}
