MemberController
| 程式 | 用途 |
|---|---|
| `@RequestBody` | 把請求中的 JSON 轉成 `MemberRegisterRequest` |
| `@Valid` | 執行 Request 裡的 `@NotBlank`、`@Size`、`@Email` 等驗證 |
| `memberService.register(request)` | 把資料交給 Service 執行註冊 |
| `@ResponseStatus(HttpStatus.CREATED)` | 正常完成後回傳 HTTP `201` |
| `void` | 成功時沒有回應內容 |