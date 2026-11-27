# 后端消息国际化：来源清单与实现说明

## 语言同步

- 前端沿用 `collab.locale`（`en`、`zh-CN`、`zh-TW`）。每次 API 请求，包括导入与导出，发送最新的 `Accept-Language`。
- `LanguageConfig` 在安全过滤器前建立请求语言，MVC 使用相同规则；结束后恢复线程上下文，避免请求之间串语言。响应带 `Content-Language` 与 `Vary: Accept-Language`。
- 默认英文；支持语言优先级及常见中文地区/文字别名（如 `zh-Hant`、`zh-HK`）。不支持、格式错误或缺失的语言回退英文。
- 接口错误保留 `status`、`message`，新增稳定 `code`；参数校验另外提供 `errors[{field,message}]`。数据库异常、解析异常和服务器异常不直接向用户泄露底层英文详情。
- STOMP CONNECT 和 SUBSCRIBE 携带最新语言。后端保存连接语言作为订阅错误的回退；通知内容通过三语快照在接收端选择，不依赖发送者或消息队列线程的语言。

## 已定位的消息来源

| 来源 | 原有消息/问题 | 处理方式 |
| --- | --- | --- |
| `UserService`、`CurrentUser` | 登录失败、未登录、密码错误/必填、用户名重复、不能禁用自己、部门代码重复/不可修改、实体不存在 | 稳定错误代码及三语资源 |
| `PermissionService`、`CellService` | 表单访问级别不足、表头/指定部门列不可编辑、仅查看、单元格越界、不能增行/增列 | 参数化消息；保留表单名称、部门代码、单元格坐标 |
| `SheetService` | 共享 OWNER、列/索引越界、未知部门、表头插删限制、至少一行/列、行列数上限、工作表命名/删除约束、创建权限、实体不存在 | 参数化消息；默认工作表名在创建时按请求语言生成 |
| `TemplateService` | 模板单元格越界、只有所属部门主管可修改、模板不存在 | 三语错误 |
| `ImportExportService`、`ExcelWorkbookReader` | 无效/空 Excel、不支持格式、没有数据、ZIP 内部条目上限（原来固定简体中文） | 三语错误；导入说明与空导出页名按请求语言生成 |
| `GlobalExceptionHandler` | 校验器英文、无权限、上传过大、意外异常；缺少参数、错误类型/JSON/枚举、错误方法/媒体类型、找不到路由、数据冲突 | 统一返回本地化响应，保留合理 HTTP 状态 |
| `SecurityConfig` | 安全过滤器的 401 无正文、403 默认响应 | 与业务异常相同的三语 JSON 格式 |
| `LocalizedErrorController` | MVC advice 之外的容器 `/error` 默认英文 | 三语兜底响应，不返回堆栈或底层异常文字 |
| `StompAuthInterceptor` | Unauthorized、No access to sheet、Unknown destination | 按帧/连接语言生成消息 |
| `NotificationService` | 关键变更/普通变更标题、布局变更标题与六种操作、共享标题与权限、空值、剩余条数 | 所有新通知生成三种语言快照 |
| `SheetLabels`、`SheetViews` | 自动生成的 Row/Header/Column 标签 | 通知和历史记录按语言显示生成的行标签；新默认列使用 A/B/C 坐标；自定义标题保留原文 |
| `DataSeeder` | 示例部门、人员、表名、模板和单元格数据包含英文 | 属于可编辑业务数据，保留原文 |
| `TemplateCodec`、`EventPublisher`、全局异常日志、Excel 启动配置检查 | 运维日志与内部异常英文 | 保留诊断原文；对外统一为本地化错误 |
| `SheetEvent`、`BroadcastListener` | 枚举、路由、公式、TRUE/FALSE、样式键等 | 属于协议或业务值，不作为提示翻译 |

## 通知存储与显示

新增 Flyway `V3__notification_languages.sql`，仅增加可空 `notification.localized_text`（LONGTEXT），保存三语 JSON 快照。原有 `title`、`message` 保留英文，兼容已有数据和旧客户端。REST DTO 的 `title/message` 按当前请求语言返回，同时附带完整 `translations`；实时推送同样携带三语内容。

通知列表、首页重要通知、铃铛预览、实时弹窗读取相同的前端显示函数。切换语言后，已加载列表与已经打开的重要通知弹窗立即更新；无需重新登录。历史记录另附 `rowLabelTranslations`，已加载的系统行标签同样立即切换。人员、部门、表单名、列标题、单元格内容保持业务原文。

历史通知没有三语字段时，通过严格匹配旧系统模板，在读取时转换标题、共享权限、布局操作和“另有 N 项”；不批量重写数据库。旧的单元格正文已将业务值与 `(empty)` 等占位词混合，无法可靠区分用户输入与系统文本，因此保留原始单元格正文。未识别或被截断的旧模板也保留原文。新通知不存在该空值歧义。

上线需同时部署前后端并让 Flyway 执行 V3；此次开发未连接实际数据库执行迁移，未对实际 RabbitMQ 推送做端到端验证。若环境存在 Flyway 初始化问题，需先按原项目部署流程解决，不能用本改动绕过迁移历史。

## 消息资源与精确来源

三个资源文件各有 **114** 个相同消息代码。下表给出每个代码的英文原文、简体/繁体译文及调用来源；动态构造的代码按所属处理器标注。

| 代码 | 英文 | 简体中文 | 繁体中文 | 来源 |
| --- | --- | --- | --- | --- |
| `error.notFound` | {0} not found | 找不到{0} | 找不到{0} | [ApiException.java:21](../backend/src/main/java/com/factory/collab/config/ApiException.java#L21) |
| `entity.Sheet` | Sheet | 表单 | 表單 | [ApiException.notFound](../backend/src/main/java/com/factory/collab/config/ApiException.java) |
| `entity.Template` | Template | 模板 | 範本 | [ApiException.notFound](../backend/src/main/java/com/factory/collab/config/ApiException.java) |
| `entity.User` | User | 用户 | 使用者 | [ApiException.notFound](../backend/src/main/java/com/factory/collab/config/ApiException.java) |
| `entity.Department` | Department | 部门 | 部門 | [ApiException.notFound](../backend/src/main/java/com/factory/collab/config/ApiException.java) |
| `entity.Notification` | Notification | 通知 | 通知 | [ApiException.notFound](../backend/src/main/java/com/factory/collab/config/ApiException.java) |
| `error.invalidCredentials` | Invalid username or password | 用户名或密码错误 | 使用者名稱或密碼錯誤 | [UserService.java:32](../backend/src/main/java/com/factory/collab/service/UserService.java#L32) |
| `error.currentPassword` | Current password is incorrect | 当前密码错误 | 目前密碼錯誤 | [UserService.java:45](../backend/src/main/java/com/factory/collab/service/UserService.java#L45) |
| `error.usernameExists` | Username already exists | 用户名已存在 | 使用者名稱已存在 | [UserService.java:59](../backend/src/main/java/com/factory/collab/service/UserService.java#L59), [UserService.java:71](../backend/src/main/java/com/factory/collab/service/UserService.java#L71) |
| `error.passwordRequired` | Password is required | 请输入密码 | 請輸入密碼 | [UserService.java:60](../backend/src/main/java/com/factory/collab/service/UserService.java#L60) |
| `error.disableSelf` | You cannot disable your own account | 不能停用自己的账号 | 不能停用自己的帳號 | [UserService.java:74](../backend/src/main/java/com/factory/collab/service/UserService.java#L74) |
| `error.departmentCodeExists` | Department code already exists | 部门代码已存在 | 部門代碼已存在 | [UserService.java:99](../backend/src/main/java/com/factory/collab/service/UserService.java#L99) |
| `error.departmentCodeImmutable` | A department's code cannot be changed | 不能修改部门代码 | 不能修改部門代碼 | [UserService.java:102](../backend/src/main/java/com/factory/collab/service/UserService.java#L102) |
| `error.templateCell` | Template cell outside its grid | 模板单元格超出范围 | 範本儲存格超出範圍 | [TemplateService.java:87](../backend/src/main/java/com/factory/collab/service/TemplateService.java#L87) |
| `error.templateOwner` | Only the owning department's manager can change this template | 只有所属部门主管可以修改此模板 | 只有所屬部門主管可以修改此範本 | [TemplateService.java:95](../backend/src/main/java/com/factory/collab/service/TemplateService.java#L95) |
| `error.cellLimit` | Cell {0} is beyond the sheet limit | 单元格 {0} 超出表单限制 | 儲存格 {0} 超出表單限制 | [CellService.java:51](../backend/src/main/java/com/factory/collab/service/CellService.java#L51) |
| `error.cellEdit` | You cannot edit {0}: {1} | 无法编辑 {0}：{1} | 無法編輯 {0}：{1} | [CellService.java:54](../backend/src/main/java/com/factory/collab/service/CellService.java#L54) |
| `error.addColumns` | Only the sheet owner can add columns | 只有表单所有者可以新增列 | 只有表單擁有者可以新增欄 | [CellService.java:60](../backend/src/main/java/com/factory/collab/service/CellService.java#L60) |
| `error.addRows` | Your department cannot add rows to this sheet | 您的部门不能向此表单新增行 | 您的部門不能向此表單新增列 | [CellService.java:63](../backend/src/main/java/com/factory/collab/service/CellService.java#L63) |
| `error.ownerGrant` | OWNER cannot be granted to other departments | 不能将所有者权限授予其他部门 | 不能將擁有者權限授予其他部門 | [SheetService.java:229](../backend/src/main/java/com/factory/collab/service/SheetService.java#L229) |
| `error.columnRange` | Column out of range | 列超出范围 | 欄超出範圍 | [SheetService.java:253](../backend/src/main/java/com/factory/collab/service/SheetService.java#L253), [SheetService.java:273](../backend/src/main/java/com/factory/collab/service/SheetService.java#L273) |
| `error.unknownDepartment` | Unknown department {0} | 未知部门 {0} | 未知部門 {0} | [SheetService.java:256](../backend/src/main/java/com/factory/collab/service/SheetService.java#L256) |
| `error.insertHeader` | Cannot insert into header rows | 不能在表头行中插入 | 不能在標題列中插入 | [SheetService.java:315](../backend/src/main/java/com/factory/collab/service/SheetService.java#L315) |
| `error.deleteHeader` | Cannot delete header rows | 不能删除表头行 | 不能刪除標題列 | [SheetService.java:323](../backend/src/main/java/com/factory/collab/service/SheetService.java#L323) |
| `error.oneRow` | A sheet needs at least one row | 表单至少需要一行 | 表單至少需要一列 | [SheetService.java:324](../backend/src/main/java/com/factory/collab/service/SheetService.java#L324) |
| `error.oneColumn` | A sheet needs at least one column | 表单至少需要一列 | 表單至少需要一欄 | [SheetService.java:340](../backend/src/main/java/com/factory/collab/service/SheetService.java#L340) |
| `error.tabBeforeName` | Add a second worksheet before naming it | 请先新增第二个工作表再命名 | 請先新增第二個工作表再命名 | [SheetService.java:403](../backend/src/main/java/com/factory/collab/service/SheetService.java#L403) |
| `error.tabName` | A worksheet name is required | 请输入工作表名称 | 請輸入工作表名稱 | [SheetService.java:404](../backend/src/main/java/com/factory/collab/service/SheetService.java#L404) |
| `error.notWorkbook` | This is not a workbook worksheet | 这不是工作簿中的工作表 | 這不是活頁簿中的工作表 | [SheetService.java:418](../backend/src/main/java/com/factory/collab/service/SheetService.java#L418) |
| `error.oneTab` | A workbook must keep at least one worksheet | 工作簿必须保留至少一个工作表 | 活頁簿必須保留至少一個工作表 | [SheetService.java:420](../backend/src/main/java/com/factory/collab/service/SheetService.java#L420) |
| `error.createManager` | Only department managers can create sheets | 只有部门主管可以创建表单 | 只有部門主管可以建立表單 | [SheetService.java:471](../backend/src/main/java/com/factory/collab/service/SheetService.java#L471) |
| `error.createDepartment` | You can only create sheets for your own department | 只能为自己的部门创建表单 | 只能為自己的部門建立表單 | [SheetService.java:478](../backend/src/main/java/com/factory/collab/service/SheetService.java#L478) |
| `error.indexRange` | Index out of range | 索引超出范围 | 索引超出範圍 | [SheetService.java:489](../backend/src/main/java/com/factory/collab/service/SheetService.java#L489) |
| `error.max.rows` | A sheet can have at most {0} rows | 表单最多可有 {0} 行 | 表單最多可有 {0} 列 | [SheetService.checkMax](../backend/src/main/java/com/factory/collab/service/SheetService.java) |
| `error.max.columns` | A sheet can have at most {0} columns | 表单最多可有 {0} 列 | 表單最多可有 {0} 欄 | [SheetService.checkMax](../backend/src/main/java/com/factory/collab/service/SheetService.java) |
| `error.accessRequired` | This action needs {0} access to '{1}' | 此操作需要对“{1}”具有{0}权限 | 此操作需要對「{1}」具有{0}權限 | [PermissionService.java:71](../backend/src/main/java/com/factory/collab/service/PermissionService.java#L71) |
| `deny.header` | header rows can only be edited by the sheet owner | 只有表单所有者可以编辑表头行 | 只有表單擁有者可以編輯標題列 | [PermissionService.java:79](../backend/src/main/java/com/factory/collab/service/PermissionService.java#L79) |
| `deny.column` | this column is reserved for {0} | 此列仅允许 {0} 编辑 | 此欄僅允許 {0} 編輯 | [PermissionService.java:82](../backend/src/main/java/com/factory/collab/service/PermissionService.java#L82) |
| `deny.view` | your department has view-only access | 您的部门仅有查看权限 | 您的部門僅有檢視權限 | [PermissionService.java:84](../backend/src/main/java/com/factory/collab/service/PermissionService.java#L84) |
| `error.invalidExcel` | The file is empty or not a valid Excel workbook | 文件为空或不是有效的 Excel 工作簿 | 檔案為空或不是有效的 Excel 活頁簿 | [ImportExportService.java:79](../backend/src/main/java/com/factory/collab/service/ImportExportService.java#L79) |
| `error.formats` | Supported formats: .xlsx, .xls, .csv | 支持的格式：.xlsx、.xls、.csv | 支援的格式：.xlsx、.xls、.csv | [ImportExportService.java:82](../backend/src/main/java/com/factory/collab/service/ImportExportService.java#L82) |
| `error.noData` | No data found in the file | 文件中没有数据 | 檔案中沒有資料 | [ImportExportService.java:84](../backend/src/main/java/com/factory/collab/service/ImportExportService.java#L84) |
| `error.excelEntries` | Excel internal entry count exceeds {0}. This limit is unrelated to row count. Simplify the file or adjust collab.excel.max-file-count and restart the backend. | Excel 文件内部条目数超过上限 {0}，此限制与表单行数无关。请精简文件，或调整 collab.excel.max-file-count 后重启后端。 | Excel 檔案內部項目數超過上限 {0}，此限制與表單列數無關。請精簡檔案，或調整 collab.excel.max-file-count 後重新啟動後端。 | [ExcelWorkbookReader.java:28](../backend/src/main/java/com/factory/collab/service/ExcelWorkbookReader.java#L28) |
| `error.unauthorized` | Not authenticated | 请先登录 | 請先登入 | [StompAuthInterceptor.java:42](../backend/src/main/java/com/factory/collab/config/StompAuthInterceptor.java#L42), [StompAuthInterceptor.java:47](../backend/src/main/java/com/factory/collab/config/StompAuthInterceptor.java#L47), [CurrentUser.java:13](../backend/src/main/java/com/factory/collab/config/CurrentUser.java#L13), [SecurityConfig.java:43](../backend/src/main/java/com/factory/collab/config/SecurityConfig.java#L43), [LocalizedErrorController.java:19](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L19) |
| `error.denied` | Access denied | 没有访问权限 | 沒有存取權限 | [SecurityConfig.java:44](../backend/src/main/java/com/factory/collab/config/SecurityConfig.java#L44), [LocalizedErrorController.java:20](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L20), [GlobalExceptionHandler.java:55](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L55) |
| `error.invalidRequest` | Invalid request | 请求内容无效 | 請求內容無效 | [LocalizedErrorController.java:25](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L25), [GlobalExceptionHandler.java:36](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L36), [GlobalExceptionHandler.java:37](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L37), [GlobalExceptionHandler.java:48](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L48), [GlobalExceptionHandler.java:51](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L51), [GlobalExceptionHandler.java:60](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L60) |
| `error.tooLarge` | File is too large | 文件过大 | 檔案過大 | [LocalizedErrorController.java:23](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L23), [GlobalExceptionHandler.java:57](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L57) |
| `error.unexpected` | Unexpected server error | 服务器发生错误，请稍后重试 | 伺服器發生錯誤，請稍後重試 | [LocalizedErrorController.java:25](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L25), [GlobalExceptionHandler.java:72](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L72) |
| `error.method` | HTTP method not supported | 不支持此请求方法 | 不支援此請求方法 | [LocalizedErrorController.java:22](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L22), [GlobalExceptionHandler.java:62](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L62) |
| `error.media` | Unsupported content type | 不支持此内容类型 | 不支援此內容類型 | [LocalizedErrorController.java:24](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L24), [GlobalExceptionHandler.java:64](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L64) |
| `error.conflict` | The data conflicts with an existing record | 数据与已有记录冲突 | 資料與現有記錄衝突 | [GlobalExceptionHandler.java:68](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L68) |
| `error.noSheetAccess` | No access to sheet {0} | 无权访问表单 {0} | 無權存取表單 {0} | [StompAuthInterceptor.java:54](../backend/src/main/java/com/factory/collab/config/StompAuthInterceptor.java#L54) |
| `error.destination` | Unknown destination | 未知的订阅地址 | 未知的訂閱位址 | [StompAuthInterceptor.java:57](../backend/src/main/java/com/factory/collab/config/StompAuthInterceptor.java#L57) |
| `validation.NotBlank` | must not be blank | 不能为空 | 不能為空 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `validation.NotNull` | is required | 为必填项 | 為必填項 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `validation.NotEmpty` | must not be empty | 不能为空 | 不能為空 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `validation.Size` | length/size must be between {0} and {1} | 长度或数量必须介于 {0} 和 {1} 之间 | 長度或數量必須介於 {0} 和 {1} 之間 | [GlobalExceptionHandler.java:45](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L45) |
| `validation.Min` | must be at least {0} | 不得小于 {0} | 不得小於 {0} | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `validation.Max` | must be at most {0} | 不得大于 {0} | 不得大於 {0} | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `validation.Pattern` | has an invalid format | 格式不正确 | 格式不正確 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `level.NONE` | no | 无 | 無 | [PermissionService.java](../backend/src/main/java/com/factory/collab/service/PermissionService.java) / [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `level.VIEW` | view | 查看 | 檢視 | [PermissionService.java](../backend/src/main/java/com/factory/collab/service/PermissionService.java) / [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `level.EDIT` | edit | 编辑 | 編輯 | [PermissionService.java](../backend/src/main/java/com/factory/collab/service/PermissionService.java) / [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `level.OWNER` | owner | 所有者 | 擁有者 | [PermissionService.java](../backend/src/main/java/com/factory/collab/service/PermissionService.java) / [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `notice.key.one` | ⚠ {0} key change in {1} | ⚠ {1} 有 {0} 项关键变更 | ⚠ {1} 有 {0} 項關鍵變更 | [NotificationService.java:84](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L84) |
| `notice.key.many` | ⚠ {0} key changes in {1} | ⚠ {1} 有 {0} 项关键变更 | ⚠ {1} 有 {0} 項關鍵變更 | [NotificationService.java:84](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L84) |
| `notice.cells.one` | {0} cell updated in {1} | {1} 更新了 {0} 个单元格 | {1} 更新了 {0} 個儲存格 | [NotificationService.java:92](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L92) |
| `notice.cells.many` | {0} cells updated in {1} | {1} 更新了 {0} 个单元格 | {1} 更新了 {0} 個儲存格 | [NotificationService.java:92](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L92) |
| `notice.layout` | Layout changed in {0} | {0} 的布局已变更 | {0} 的版面配置已變更 | [NotificationService.java:57](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L57) |
| `notice.layoutBody` | The sheet layout changed. | 表单布局已变更。 | 表單版面配置已變更。 | [NotificationService.java:166](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L166) |
| `notice.shared` | {0} shared "{1}" with your department | {0} 向您的部门共享了“{1}” | {0} 向您的部門共用了「{1}」 | [NotificationService.java:117](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L117) |
| `notice.access` | Your department now has {0} access. | 您的部门现在具有{0}权限。 | 您的部門現在具有{0}權限。 | [NotificationService.java:118](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L118) |
| `notice.more` | …and {0} more | …另有 {0} 项 | …另有 {0} 項 | [NotificationService.java:154](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L154) |
| `notice.empty` | (empty) | （空） | （空） | [NotificationService.java:172](../backend/src/main/java/com/factory/collab/service/NotificationService.java#L172) |
| `notice.INSERT_ROWS` | {0} ({1}) inserted {2} row(s) at row {3}. | {0}（{1}）在第 {3} 行插入了 {2} 行。 | {0}（{1}）在第 {3} 列插入了 {2} 列。 | [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `notice.DELETE_ROWS` | {0} ({1}) deleted {2} row(s) from row {3}. | {0}（{1}）从第 {3} 行删除了 {2} 行。 | {0}（{1}）從第 {3} 列刪除了 {2} 列。 | [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `notice.INSERT_COLS` | {0} ({1}) inserted {2} column(s) at {3}. | {0}（{1}）在 {3} 列插入了 {2} 列。 | {0}（{1}）在 {3} 欄插入了 {2} 欄。 | [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `notice.DELETE_COLS` | {0} ({1}) deleted {2} column(s) from {3}. | {0}（{1}）从 {3} 列删除了 {2} 列。 | {0}（{1}）從 {3} 欄刪除了 {2} 欄。 | [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `notice.APPEND_ROWS` | {0} ({1}) added {2} row(s). | {0}（{1}）新增了 {2} 行。 | {0}（{1}）新增了 {2} 列。 | [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `notice.APPEND_COLS` | {0} ({1}) added {2} column(s). | {0}（{1}）新增了 {2} 列。 | {0}（{1}）新增了 {2} 欄。 | [NotificationService.java](../backend/src/main/java/com/factory/collab/service/NotificationService.java) |
| `label.column` | Column {0} | 列 {0} | 欄 {0} | 预留列标签资源；默认列使用 A/B/C 坐标，避免误译自定义标题 |
| `label.row` | Row {0} | 行 {0} | 列 {0} | 见三语资源文件 |
| `label.header` | Header | 表头 | 標題 | 见三语资源文件 |
| `label.tab` | Sheet {0} | 工作表 {0} | 工作表 {0} | [SheetService.java:370](../backend/src/main/java/com/factory/collab/service/SheetService.java#L370), [SheetService.java:379](../backend/src/main/java/com/factory/collab/service/SheetService.java#L379), [ImportExportService.java:341](../backend/src/main/java/com/factory/collab/service/ImportExportService.java#L341) |
| `label.imported` | Imported from {0} | 从 {0} 导入 | 從 {0} 匯入 | [ImportExportService.java:90](../backend/src/main/java/com/factory/collab/service/ImportExportService.java#L90) |
| `field.username` | Username | 用户名 | 使用者名稱 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.password` | Password | 密码 | 密碼 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.displayName` | Display name | 显示名称 | 顯示名稱 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.departmentId` | Department | 部门 | 部門 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.role` | Role | 角色 | 角色 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.code` | Code | 代码 | 代碼 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.name` | Name | 名称 | 名稱 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.description` | Description | 描述 | 描述 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.currentPassword` | Current password | 当前密码 | 目前密碼 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.newPassword` | New password | 新密码 | 新密碼 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.rows` | Rows | 行数 | 列數 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.cols` | Columns | 列数 | 欄數 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.headerRows` | Header rows | 表头行数 | 標題列數 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.r` | Row | 行 | 列 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.c` | Column | 列 | 欄 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.v` | Cell value | 单元格内容 | 儲存格內容 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.s` | Cell style | 单元格样式 | 儲存格樣式 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.edits` | Edits | 编辑内容 | 編輯內容 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.op` | Operation | 操作 | 操作 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.index` | Position | 位置 | 位置 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.count` | Count | 数量 | 數量 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.title` | Title | 标题 | 標題 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.width` | Width | 宽度 | 寬度 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.permissions` | Permissions | 权限 | 權限 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.level` | Access level | 访问级别 | 存取層級 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.definition` | Template definition | 模板定义 | 範本定義 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.rowCount` | Rows | 行数 | 列數 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `field.colCount` | Columns | 列数 | 欄數 | [GlobalExceptionHandler.java](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java) / [Dtos.java](../backend/src/main/java/com/factory/collab/dto/Dtos.java) / [TemplateDefinition.java](../backend/src/main/java/com/factory/collab/dto/TemplateDefinition.java) |
| `error.resourceNotFound` | Resource not found | 找不到请求的资源 | 找不到請求的資源 | [LocalizedErrorController.java:21](../backend/src/main/java/com/factory/collab/config/LocalizedErrorController.java#L21), [GlobalExceptionHandler.java:66](../backend/src/main/java/com/factory/collab/config/GlobalExceptionHandler.java#L66) |

## 验证范围

- 后端：21 项测试通过，Maven 可运行包构建通过；HTTP 请求测试覆盖三语登录错误、401/403、请求间语言隔离、字段校验及参数、坏 JSON/缺少查询参数、404 与容器兜底；另测消息资源一致性、参数安全插值、历史兼容、通知生成与推送载荷、STOMP 连接/订阅语言。
- 前端：28 项 Vitest 测试通过，生产构建通过（保留原有大分块体积提示）；新增覆盖逐请求语言头、导出错误 Blob、网络/超时回退、已有通知切换、已挂载弹窗语言切换及用户数据保留。
- 测试使用模拟仓库/消息总线及内存 Vue 渲染环境，不依赖数据库、RabbitMQ 或真实浏览器。Mockito 使用 subclass 模式，避免依赖本机 JVM 的动态代理附加能力。
