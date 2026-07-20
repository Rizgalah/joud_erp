# Fix Maven Multi-Module Configuration & Classpath Errors

The widespread errors you are seeing in the application (like `ExchangeRateBean.java` not finding `AuthFilter` or `UserSession`) are caused by a severely misconfigured Maven architecture. The IDE is completely failing to recognize the project structure, preventing it from resolving cross-module dependencies and inherited libraries.

Here is why:
1.  **Parent POM Name**: Your parent POM is named `joud-erp-parent-pom.xml`. Maven and your IDE expect the root file to be named `pom.xml`. Because of this, the IDE doesn't know this is a multi-module project.
2.  **Missing `<parent>` Links**: All of your sub-modules (`joud-core`, `joud-accounting`, etc.) are missing the `<parent>` configuration. As a result, they do not inherit the Java version, dependency management, or project structure from the root.
3.  **The `joud-core` Packaging Problem**: `joud-core` is packaged as a `war` file. However, other modules (like `joud-accounting`) depend on its Java classes. By default, Maven does not expose Java classes from a `war` file to other modules on the compilation classpath.

## User Review Required

> [!WARNING]
> This plan will modify all the `pom.xml` files in the project. It will rename `joud-erp-parent-pom.xml` to `pom.xml` at the root folder. Please ensure you do not have any unsaved changes in your POM files before approving this plan.

## Proposed Changes

### `c:\joud_erp\`

#### [NEW] `pom.xml`
Rename `joud-erp-parent-pom.xml` to `pom.xml`. No changes to its actual content are needed.

#### [DELETE] `joud-erp-parent-pom.xml`
This file will be deleted as it is being renamed to `pom.xml`.

---

### `joud-core`

#### [MODIFY] `pom.xml`
- Add the `<parent>` block pointing to `com.joud:joud-erp-parent:1.0.0`.
- Remove duplicate `<properties>` like `maven.compiler.source` and versions since they are now inherited from the parent.
- Remove `<version>` from dependencies (like Jakarta EE, Hibernate, PostgreSQL, Jasper, POI, BCrypt) since they are managed by the parent.
- Add `<attachClasses>true</attachClasses>` to the `maven-war-plugin`. This tells Maven to generate a `joud-core-1.0.0-classes.jar` file that other modules can depend on to get the Java classes.

---

### `joud-accounting`, `joud-hr`, `joud-trade`, `joud-inventory`, `joud-dashboard`

#### [MODIFY] `pom.xml` (in each module)
- Add the `<parent>` block pointing to `com.joud:joud-erp-parent:1.0.0`.
- Remove duplicate `<properties>` and `<version>` tags from managed dependencies.
- Update the dependency on `joud-core` to include `<classifier>classes</classifier>` and `<type>jar</type>`, so the compiler knows to look for the generated classes JAR instead of the WAR file.

---

### `joud-app`

#### [MODIFY] `pom.xml`
- Add the `<parent>` block.
- Remove duplicate properties and `<version>` tags from dependencies.
- Update the dependency on `joud-core` to include both the `war` (for the overlay you configured) and the `jar` with `<classifier>classes</classifier>` (for any Java code compilation in `joud-app`).

## Verification Plan

### Automated Tests
- Run `mvn clean compile` in the root `c:\joud_erp` directory to ensure all modules compile successfully without any missing dependency or classpath errors.
- Verify that IDE errors disappear once the Maven project is reloaded.
