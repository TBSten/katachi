[Ktor sample app](../README.md)

# Architecture definition

How the files that declare the architecture are laid out

| Role | Summary |
|---|---|
| [Architecture definition entry](./ArchitectureDefinitionEntry.md) | The one file whose architecture { } gathers every group, so the definition is read from here |
| [Group definition](./GroupDefinition.md) | One `<Name>Group.kt` per group, in the directory named after the group, listing the roles it is made of |
| [Role definition](./RoleDefinition.md) | One `<Name>Role.kt` per role: the extension function that declares it, and the private parts only it uses |
| [Architecture test](./ArchitectureTest.md) | The test that checks the whole project against the definition |

## Placement in this group

```
architecture-test/src/test/kotlin/com/example/
  ProjectArchitecture.kt  Architecture definition entry
  **/
    *Group.kt             Group definition
    *Role.kt              Role definition
  */
    *Group.kt             Group definition
    *Role.kt              Role definition
  *Test.kt                Architecture test
```
