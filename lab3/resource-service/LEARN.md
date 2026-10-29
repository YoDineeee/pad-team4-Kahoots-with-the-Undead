# How resource-service works

It has the same structure as zombie-service (read its `LEARN.md` first).
Only the differences are explained here.

## 1. The server creates the ID

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@JsonProperty(access = JsonProperty.Access.READ_ONLY)
private String pointId;
```

`@GeneratedValue` makes a random UUID when the point is saved.
`READ_ONLY` means `point_id` appears in responses, but a `point_id` sent by the client is ignored.

## 2. A rule that annotations cannot check

`@PositiveOrZero` checks one field. "amount must not be bigger than max_amount" compares two fields,
so it lives in the service (`checkAmountFits`). It runs on create and when PATCH changes `max_amount`.

## 3. Filters with method names

```java
List<ResourcePoint> findByRoomId(String roomId);
List<ResourcePoint> findByResource(String resource);
List<ResourcePoint> findByRoomIdAndResource(String roomId, String resource);
```

Spring reads the method name and writes the SQL (`WHERE room_id = ?`).
The service picks which one to call, depending on which query parameters were sent.

## 4. PATCH allows only two fields

`ResourcePointUpdate` contains only `resource` and `max_amount`.
Because unknown JSON fields are rejected (`fail-on-unknown-properties=true`),
sending `amount` or `room_id` returns 400.

## 5. Database

Same idea as zombie-service, with its own database: container `resource-db`,
database `resources`, volume `resource-db-data`, credentials from `.env`.
Each service owns its data; the other service cannot read this database directly.
