[Ktor sample app](../README.md)

# Domain

The application-specific behaviour, and the values it acts on

The layer that says what this application does. It knows nothing about HTTP, nor about
where values are stored.

Services and models sit together because the behaviour and the values it acts on change
for the same reasons. If `Health` gains a field, what `HealthService` returns changes too.
Conversely, this layer does not change when the data source changes, which is why the data
layer is separate.

Models carry `@Serializable` and are used as-is as the API response body. This is a place
where one decision has been made not to separate domain models from API payloads; if the
two start to drift apart, it can be undone by adding a DTO role to the API layer.

| Role | Summary |
|---|---|
| [Service](./Service.md) | Owns one application-specific behaviour, realized by combining Repositories |
| [Model](./Model.md) | The values the domain handles, used as-is for API input and output too |

## Placement in this group

```
:
  src/main/kotlin/**/
    service/*Service.kt  Service
    model/*.kt           Model
```

## Forbidden contents

What must not be placed here is Ktor types such as `Route` and `call`, and data source
details such as connection targets and queries.
