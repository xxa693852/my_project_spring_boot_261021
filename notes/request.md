@NotBlank：檢查字串，拒絕 null、""、"   "。
@NotNull：檢查是否為 null，因此 false 可以通過。
@Min(1)：檢查數字至少為 1；你搭配 @NotNull 的寫法正確。

一些資料的寫法

    @NotBlank(message = ResponseMessages.MEMBER_NAME_REQUIRED) //不能為空白
    @Size(max = 50, message = ResponseMessages.MEMBER_NAME_MAX)
    private String name;

    @NotBlank(message = ResponseMessages.MEMBER_ACCOUNT_REQUIRED)
    @Size(max = 50, message = ResponseMessages.MEMBER_ACCOUNT_MAX)
    private String account;

    @NotBlank(message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    @Size(max = 255, min = 6, message = ResponseMessages.MEMBER_PASSWORD_SIZE) //密碼最大為6~12個字
    private String password;


    @NotBlank(message = "活動名稱不能空白")
    @Size(max = 100, message = "活動名稱最多100字")
    private String title;

    @Email(message = ResponseMessages.MEMBER_EMAIL_INVALID)
    @NotBlank (message = ResponseMessages.MAIL_ADDRESS_REQUIRED )
    @Size(max = 254, message = ResponseMessages.MEMBER_EMAIL_MAX)
    private String email;


    @NotBlank (message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    @Size(max =255, min = 6 , message = ResponseMessages.MEMBER_PASSWORD_SIZE)
    private  String password;


    @NotNull(message = "報名截止時間不能空白")
    private LocalDateTime registrationDeadline;
    
    @NotNull(message = "人數上限不能空白")
    @Min(value = 1, message = "人數上限至少1人")
    private Integer capacity;

    @NotNull(message = "請指定是否新手友善")
    private Boolean beginnerFriendly;