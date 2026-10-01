[Custom processor sample](../README.md)

# Application

A small app that only reads notes and lists them. What the processors read

The application itself. All it does is read notes from a store and list them on standard
output.

It is small on purpose. This sample is about the three processors in the `testing` group, and
the application is there to provide what they read. For layers, see `api` / `domain` / `data`
in `sample/jvm`.

The roles are still split into three because both the output of `RoleFileCount` and the table
of `RoleTable` should have several rows. With only one, the output would not show what a
processor did.

| Role | Summary |
|---|---|
| [Entrypoint](./Entrypoint.md) | Starting the process. The only file that has `main()` |
| [Model](./Model.md) | The values the application handles. Holds data classes, enums and value objects |
| [Store](./Store.md) | Takes responsibility for where values come from. For now, fixed values in memory |

## Placement in this group

```
src/main/kotlin/com/example/
  Main.kt     Entrypoint
  model/*.kt  Model
  store/*.kt  Store
```

## Forbidden contents

Definitions and processor code must not be placed here. Both live in `:architecture-test` and
are covered by the roles of the `testing` group.
