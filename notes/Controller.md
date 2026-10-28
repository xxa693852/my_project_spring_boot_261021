MemberController
| 程式 | 用途 |
|---|---|
| `@RequestBody` | 把請求中的 JSON 轉成 `MemberRegisterRequest` |
| `@Valid` | 執行 Request 裡的 `@NotBlank`、`@Size`、`@Email` 等驗證 |
| `memberService.register(request)` | 把資料交給 Service 執行註冊 |
| `@ResponseStatus(HttpStatus.CREATED)` | 正常完成後回傳 HTTP `201` |
| `void` | 成功時沒有回應內容 |


註解	資料從哪來
@PathVariable	網址路徑，例如 /events/{eventId}
@RequestBody	前端傳來的 JSON
@RequestAttribute	後端先前放進這次 request 的資料