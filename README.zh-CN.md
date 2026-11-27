# 3DGames

[English](README.md) | [简体中文](README.zh-CN.md)

Minecraft 3D 桌游插件。棋子、卡牌、骰子与棋盘直接显示在世界中，提供单人进度、多人房间、陪练、观战和私有手牌。

同一份 JAR 支持 Paper、Purpur、Spigot、CraftBukkit 与 Folia，使用原生 Dialog 菜单，无需客户端模组。CraftEngine、Vault、PlaceholderAPI 和资源包均为可选项。

[兼容性](#兼容性) · [安装](#安装) · [操作与菜单](#操作与菜单) · [游戏](#游戏) · [配置](#配置与语言) · [资源包](#可选资源包) · [构建](#构建) · [源码目录](#源码目录) · [许可](#许可)

> 可从 [v1.0.1 Release](https://github.com/GoldenEggOVO/3DGames/releases/tag/v1.0.1) 下载插件和配套资源包。该标签包含两个附件对应的源码。

## 兼容性

| 服务端 | 已测试的 Minecraft 版本 | 结果 |
| --- | --- | --- |
| Paper | 26.1.1、26.1.2、26.2、26.3 | 支持 |
| Purpur | 26.1.2、26.2、26.3 | 支持 |
| Spigot／CraftBukkit | 26.1、26.1.1、26.1.2、26.2、26.3 | 支持 |
| Folia | 26.1.2、26.2、26.3 | 支持 |

需要 **Java 25**。在上述 20 个平台／版本组合中，牌面朝向和全部 30 款游戏的操作记录均与 Paper 26.2 基准一致。发布版本通过了 1,472 项 Java 测试和 147 项资源检查。

Spigot／CraftBukkit 的 26.1、26.1.1 使用官方历史提交精确重建后验证，当前同名 BuildTools 入口实际指向 26.1.2。Paper 当前没有提供 26.1 构建，Purpur 与 Folia 没有提供 26.1、26.1.1 构建。表格覆盖指定构建，不包含所有历史 build、快照或未来版本。

Paper／Purpur 检查使用代理玩家和真实世界／实体；Spigot／CraftBukkit 使用真实 ServerPlayer 与内存连接。Folia 26.3 另通过了双图形客户端的菜单、资源包下载、固定视角、方向输入、跨区域操作和断线／停服恢复检查。其他 Folia 版本验证真实区域实体和存档恢复。自动检查与实际画面、声音、手感验收分别处理。

服务端差异集中在 `Core/platform/`，菜单与游戏规则共用。Folia 按区域调度棋桌操作，按玩家调度器处理玩家操作；可选插件也需要支持所用服务端。

Folia 的部分原生异步传送不触发 `PlayerTeleportEvent`，这是已复现的[上游限制](https://github.com/PaperMC/Folia/issues/490)。通过此事件取消传送的其他插件需要额外核对。更换 JAR 时完整停服再启动，第三方热卸载不在兼容范围内。

## 安装

1. 准备 Java 25 与上表中的服务端，按[构建](#构建)生成当前插件；已发布版本也可从 [Releases](https://github.com/GoldenEggOVO/3DGames/releases) 下载。
2. 完全停服，备份世界与 `plugins/3DGames/`。
3. 将一份 `3DGames-*.jar` 放进 `plugins/`，不要安装 `original-*.jar`。
4. 启动后执行 `/3dgames`，选择“单人游戏”或“多人游戏”。需要中文时设置 `language: zh_CN`。

直接安装本插件即可使用原版模型。需要资源包模型时，可额外安装 CraftEngine，默认 `auto` 模式会选择混合显示并加入其统一资源包，见[可选资源包](#可选资源包)。

## 操作与菜单

主页提供“单人游戏”和“多人游戏”，资源包开关位于“菜单设置”。两种模型模式共用原生 Dialog 文字按钮，游戏人数和说明放在悬停提示中，列表使用原生滚动。在任意页面按 Esc 或点击底部的“关闭菜单”即可回到游戏。点击页面说明下方的黄色 **[返回]** 可回到上一级。金额输入使用原生输入框并校验。

所有桌子都可以潜行＋右键桌面打开房间菜单。棋子、卡牌、骰子和计分格直接在桌面操作。多人房主创建房间后，可以等待好友加入并准备，或使用“补齐陪练并开始”；选择规则后创建的房间不再更换规则。

| 游戏 | 主要操作 |
| --- | --- |
| 棋类 | 先选棋子，再选合法目标；预选与按钮反馈只向操作玩家显示 |
| 麻将 | 按住疾跑键（默认 Ctrl）进入固定看桌视角，松开退出 |
| 2048 | 按一次疾跑键切换固定俯视，以移动键 W/A/S/D 滑动，再按一次退出 |
| 猜颜色 | 点当前列孔位，再点六色托盘；右键或滚轮切色。橙色钉表示颜色与位置全对，白色钉表示只有颜色对 |
| 海战棋 | 左键选船和落船，右键旋转或撤回；确认布阵后点击对方海域射击 |
| 大富翁交易 | 点棋盘上绿色标记的地产选择或取消，青色为已选择；确认桌面草稿后提交提案 |

单人游戏页提供“继续单人进度”。每款游戏独立保存；保存并离开会释放桌位，掉线和停服也保存。重新开始同款游戏前需确认覆盖已有进度。

## 游戏

| 游戏 | 人数 | 桌面内容 |
| --- | --- | --- |
| 象棋、国际象棋 | 2 | 棋盘与立体棋子 |
| 五子棋 | 2 | 黑白棋子 |
| 黑白棋 | 2 | 翻面棋子与动画 |
| 四子棋 | 2 | 竖直棋盘与落子 |
| 围棋 | 2 | 9／13／19 路棋盘 |
| 中国跳棋 | 2、3、4、6 | 星形棋盘与彩色棋子 |
| 西洋跳棋 | 2 | 8×8 棋盘与升王 |
| Ludo | 2～4 | 十字棋盘、棋子与骰子 |
| 彩八 | 2～5 | 私有手牌、摸牌堆与颜色按钮 |
| 斗地主 | 3 | 叫地主、选牌与公开地主牌 |
| 骗子酒馆 | 2～4 | 暗出牌、质疑与局内轮盘 |
| 快艇骰子 | 2～4 | 五颗骰子、最多三掷、保留槽与十二项计分 |
| 德州扑克 | 2～6 | 公共牌、下注、庄家标记与筹码 |
| 麻将 | 4 | 私有手牌、牌河与副露 |
| 2048、纸牌接龙 | 1 | 方向滑动、翻牌与叠牌、可保存进度 |
| 猜颜色 | 1～2 | 六色四位密码、十次猜测、双人交换出题 |
| 记忆卡牌 | 1～4 | 三十六张卡牌、自动盖回、公开配对计分 |
| 播棋、直棋、六贯棋 | 2 | 标准播子、九子三连、十一阶连通与首步交换 |
| 点格棋 | 2～4 | 六乘六点阵、围格连走、并列结算 |
| 双陆棋 | 2 | 完整用骰、中栏、脱离与可选倍数骰 |
| 海战棋 | 2 | 私有十乘十海域、手动或自动布阵、交替射击 |
| 将棋、五将棋 | 2 | 升变、持驹打入、王安全与重复局面判决 |
| 大富翁 | 2～6 | 四十格、购买拍卖、建设库存、抵押交易与连锁破产 |

桌体以低饱和深木色和深绿色为主，棋盘保留各游戏常见配色，提供原版和资源包两套显示。桌面按钮统一深灰底、青色细边和浅色文字；双陆棋、大富翁在独立侧桌掷两颗骰子。桌面上的格子和近侧控制按钮可直接点击，移动类游戏先选棋子再选目标，多候选动作也在桌面选择；潜行＋右键仍打开房间管理菜单。大富翁的拍卖和交易使用桌面上的金额加减与资产选择按钮，确认草稿后才提交提案；地产局内资金与外部奖池独立。

单人容量的 2048、纸牌接龙、猜颜色和记忆卡牌创建后直接开始。选择“保存并离开”会释放桌位；单人游戏页“继续单人进度”可选择并恢复已保存的游戏，`/3dgames resume` 恢复最近保存的进度。每款游戏独立保存；重新开始已有进度的同一游戏时需要确认覆盖。掉线和停服也保存。单人不使用入场费、下注或经济奖励。进度文件在 `solo/<玩家UUID>.<游戏ID>.json`；旧格式仍可读取，替换旧格式时先备份到 `solo/legacy-backups/`。损坏文件不自动删除。长局达到动作记录安全预算时保留进度，不自动判为完成。

支持准备、陪练、观战、再来一局和重启恢复。房间创建后规则锁定，需要更换规则时创建新房间。麻将提供广东、四川、台湾和日本立直四套房规。麻将分数和德州筹码仍为局内点数，可选经济功能按完整对局收取费用和发放奖励。具体规则可在菜单或 `/3dgames rules` 中查看。

原版扑克和彩八正面为 32×48 像素，每张使用 24 个薄头颅；公开暗牌背使用两个方块。签名纹理已内置，运行时无需 MineSkin API Key。资源包卡牌每张使用一个显示实体。暗手正面仅本人可见。

## 指令与权限

| 指令 | 用途 |
| --- | --- |
| `/3dgames [menu]` | 打开主菜单 |
| `/3dgames create <kind> [players]` | 创建房间 |
| `/3dgames join <room-prefix>` | 加入开放房间 |
| `/3dgames ready`／`bots` | 准备／房主补充陪练 |
| `/3dgames resume`／`leave` | 返回桌面／离开房间 |
| `/3dgames rematch` | 准备再来一局 |
| `/3dgames move <action>` | 执行合法动作，例如 `drop:3` |
| `/3dgames rules [kind]` | 查看规则 |
| `/3dgames reload-language` | 管理员重载语言文件 |
| `/3dgames reload-pack` | 管理员重建并重新发送本插件资源包 |
| `3dgames status` | 控制台查看房间数量 |

游戏标识：`xiangqi`、`gomoku`、`chess`、`ludo`、`checkers`、`draughts`、`reversi`、`go`、`go9`、`go13`、`connectfour`、`color-eight`、`mahjong`、`yacht`、`doudizhu`、`liars-bar`、`texas-holdem`、`2048`、`mancala`、`nine-mens-morris`、`dots-and-boxes`、`mastermind`、`hex`、`memory`、`backgammon`、`battleship`、`klondike`、`shogi`、`mini-shogi`、`monopoly`。其中 `checkers` 是中国跳棋，`go` 是 19 路围棋。

例如先执行 `/3dgames create connectfour`，再执行 `/3dgames bots`。指令创建使用默认规则，自定义规则请使用菜单。Tab 会提示游戏、人数、房间和合法动作。`3dgames.use` 默认所有玩家可用，`3dgames.admin` 默认 OP，用于管理与语言重载。

## 配置与语言

配置文件位于 `plugins/3DGames/config.yml`：

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `language` | `en_US` | `languages/` 中的语言文件名 |
| `max-rooms` | `12` | 房间数量上限 |
| `reconnect-seconds` | `120` | 离线重连宽限秒数 |
| `idle-room-minutes` | `30` | 空闲房间超时分钟数 |
| `turn-seconds` | `60` | 默认回合时限，部分游戏有独立时限 |
| `sounds.enabled` | `true` | 游戏与界面音效开关 |
| `sounds.volume` | `1.0` | 0～1 的音量倍率 |
| `rendering.mode` | `auto` | 有已启用的 CraftEngine 时使用混合显示，否则使用原版；可手动指定 `vanilla`、`mixed` 或 `resource-pack` |
| `rendering.resource-pack.source` | `craftengine` | 加入 CraftEngine 统一包；可指定 `local` 独立下载或 `external` 外部 ZIP |
| `rendering.resource-pack.url` | 空 | 外部模式中与 JAR 匹配的资源包直链 |
| `rendering.resource-pack.local.port` | `8137` | 仅 `local` 模式使用的独立下载端口 |
| `rendering.resource-pack.local.public-url` | 空 | 可选公开基础地址；为空时使用玩家进服地址和下载端口 |

中文设置为 `language: zh_CN`。可编辑首次启动生成的 `languages/en_US.yml`、`languages/zh_CN.yml`，或复制一份作为其他语言。保留消息键名与 `{player}` 等参数名；支持 MiniMessage 样式及旧颜色码，语言文件不能定义回调或指令。

修改翻译或语言选项后执行 `/3dgames reload-language`，其他配置修改后重启。标准英文和中文文件缺少的条目使用对应的内置翻译补全，不会改写你的文件；其他语言缺失的条目回退到英文。重载校验失败时保留原语言。菜单布局由代码维护，标题和说明来自翻译文件。

`rooms.json` 保存房间、桌子位置和动作历史；`table-map-ids.yml` 保存插件使用的地图编号。启用收费对局后还会生成资金账本 `economy.json`。升级时保留这些文件，不在开服期间手工修改生成的数据。

## 可选经济与变量

Vault 和 PlaceholderAPI 都是可选的服务端插件，客户端不需要安装。它们的接口只用于编译，不打包进本插件。Vault 还需要可用的经济服务；兼容标准接口的经济插件均通过相同方式对接，具体实现仍需验证。

经济功能默认关闭。需要启用时修改 `config.yml` 并重启：

```yaml
economy:
  enabled: true
  stakes:
    enabled: true
    minimum: 1.0
    maximum: 1000.0
```

所有游戏共用管理员设置的正金额上下限。新房间金额默认为 **0**。房主在房间菜单设置每人金额，保存后清空全员准备；每人确认显示金额后，开局才扣款。机器人对局金额为 0。桌游分配玩家实际缴纳的奖池；德州筹码和麻将点数仍为局内分数。

中途主动退出视为认输，立即结束本局，其余对手平分奖池；斗地主确定地主后按阵营判负，叫地主前按个人结算。掉线或离开桌子所在世界时暂停操作，允许 `reconnect-seconds`（默认 120 秒）内返回；超时按认输处理。多人同时超时则共同判负，所有人均超时按平局退款。重新启动服务器后，恢复中的下注局重新给予一次完整重连宽限。分账按经济插件的小数精度，不能整除的最小货币单位按原座位顺序分配。

平局、管理员取消及开局失败退还确认扣除的金额。认输后退出者可以加入其他房间；原桌保留对手的结算页面，人员不全时须重新建房。正常结束后可以全员再次确认相同金额再战，每次再战是新的收费对局。房间列表显示每人赌注和满员奖池；余额及差额仅向本人显示。结算分别显示已付赌注、已收退款、已收奖池份额、净收支和待到账金额，已完成的支付不会因离开结果页面而撤销。

`economy.stakes.enabled` 控制桌游房主的金额设置。旧版固定费用房间和资金记录继续兼容读取；新建房间总是从金额 0 开始。

每局捕获金额与参与者。明确失败的退款、派奖每 30 秒或重启后重试。Vault 没有交易幂等编号，崩溃或提供者异常可能使交易结果未知；这类记录保留在`economy.json`，**不会自动重复执行**，须由管理员对照经济插件记录核查。损坏或无法写入的账本会阻止正金额对局并保留原文件。未决付款会阻止该玩家新开正金额局。桌游账本使用 schema 2 并兼容读取旧版 schema 1；有资金记录时不要直接降级到不支持 schema 2 的旧插件，降级版会拒绝读取该账本。

语言文件使用当前内置目录提供的语义键。离开提示按房间状态读取 `menu.leave.lobby`、`menu.leave.active`、`menu.leave.finished` 和 `menu.leave.refund`。

安装 PlaceholderAPI 后自动注册内置的 `3dgames` 扩展，`/papi reload` 后仍保留。可用于计分板、玩家列表或全息文字：

| 变量 | 含义 |
| --- | --- |
| `%3dgames_rooms%`、`%3dgames_players%` | 房间数、真人席位数，包含暂时离线席位 |
| `%3dgames_in_room%`、`%3dgames_room_id%`、`%3dgames_room_name%` | 是否入座、完整房间编号、本地化房间名称 |
| `%3dgames_game%`、`%3dgames_game_id%` | 本地化游戏名称、固定游戏标识 |
| `%3dgames_state%`、`%3dgames_state_id%` | 本地化房间状态、固定小写状态标识 |
| `%3dgames_room_players%`、`%3dgames_room_humans%`、`%3dgames_room_bots%`、`%3dgames_room_capacity%` | 总席位数、真人数、机器人数、容量 |
| `%3dgames_ready%`、`%3dgames_your_turn%`、`%3dgames_turn_player%` | 是否准备、是否轮到自己、当前行动玩家 |
| `%3dgames_economy_available%`、`%3dgames_balance%`、`%3dgames_balance_formatted%` | 经济服务是否可用、余额、经济插件格式化后的余额 |

房间数据每秒刷新；被请求的余额通过插件调度任务最多每五秒刷新一次，首次请求在下一次刷新前为空。没有经济服务时余额为空。未入座玩家的房间文字为空、人数为零、布尔值为 `false`；未知变量保持未解析。变量回调只读取不可变快照，不会读取隐藏手牌，也不会在回调里操作数据库或经济插件。

## 可选资源包

新安装时，将本插件和 CraftEngine 的 JAR 放入 `plugins/` 后启动即可。插件自动补充 `plugins/CraftEngine/resources/3dgames/` 下缺少的注册文件和内置物品条目，保留现有自定义条目，将桌游素材加入 CraftEngine 统一资源包，复用其下载服务。CraftEngine 默认的自托管 `port: auto` 复用 Minecraft 游戏端口，普通直连服务器不需要额外开放 8137。

默认配置如下；已有明确的配置保留，切换旧安装时修改后重启：

```yaml
rendering:
  mode: auto
  resource-pack:
    source: craftengine
```

CraftEngine 已有的资源内容与托管配置保留。如果它的托管被关闭，或服务器使用 Velocity／BungeeCord、特殊端口映射，需要按 CraftEngine 的托管说明配置玩家可访问的地址。插件不会自动改写其他插件的网络配置。

资源包重建期间，请等待完成后再执行 CraftEngine 的其他重载或生成指令。

首次启用时，插件将内置素材释放到 `plugins/3DGames/resource-pack/`，生成 `plugins/3DGames/resource-pack.zip` 作为统一包的输入。实际下载的是 CraftEngine 生成的完整统一包。更新插件时只补充缺少的默认文件，已有自定义图片不会被覆盖。

**日常换图不需要编译，也不需要 Python、Maven 或替换 JAR：**

1. 在服务端资源文件夹中覆盖同名、同路径的图片，例如 `assets/3dgames/textures/item/tabletop/color_eight/color_eight_blue_2_face.png`。
2. 执行 `/3dgames reload-pack`，需要管理员权限；控制台也可执行。
3. 插件在后台校验和打包，再重建并发布 CraftEngine 统一包，为已启用资源包的在线玩家加载新版。图片或 JSON 损坏时保留原包；桌游内容没有变化时不重复下发。房间和玩家选择保持不变。其他 CraftEngine 内容的修改使用它自己的重载指令。

资源来源默认使用 `craftengine`，仅填写 `url` 不会自动切换到外部模式。独立托管可指定 `source: local`；已有 HTTP／HTTPS 服务可指定 `source: external`，并填写与 JAR 内置校验值匹配的交付 ZIP 直链。修改资源来源后重启。

独立模式 `source: local` 使用 `local.bind-address`、`local.port` 和 `local.public-url`。默认监听 `0.0.0.0:8137`；公开地址为空时使用玩家进服地址与下载端口。这种独立托管方式需要开放下载端口，或将公开 HTTP(S) 地址的 `/3dgames/` 反向代理到独立服务。

默认 `auto` 在启动时检测 CraftEngine：已启用则选择 `mixed`，未安装或未启用则选择 `vanilla`。已有明确的模式设置保持不变，升级旧配置时可手动改成 `auto`。安装或移除 CraftEngine 后重启以重新检测。

`vanilla` 无需 CraftEngine 或资源包。`mixed` 允许玩家单独开关桌游显示，资源包不可用时显示原版。关闭开关只撤回本插件发出的加载请求；CraftEngine 或其他插件自行发送的包继续由它们管理。`resource-pack` 要求资源包加载成功、模型注册就绪后才能进行游戏。三种显式模式仍可用于手动覆盖自动选择。修改游戏逻辑、增加资源标识或更改原版头颅纹理仍需更新插件；此重载命令处理客户端资源包的图片、模型与音效。

## 升级

停服并完整备份插件数据后替换 JAR，保留现有配置、自定义翻译、房间和其他插件。当前 schema 1 房间文件继续支持，已退役玩法的旧格式不自动转换。升级前可用候选 JAR 验证备份：

```sh
java -cp target/3DGames-1.0.1.jar GoldenEggOVO.TDGames.Multiplayer.room.RoomReplayVerifier /backup/3DGames/rooms.json
```

使用外部资源分发时同时更新匹配的 ZIP 和 URL；统一包与本地模式从 JAR 中补充缺少的文件。启动后检查房间恢复、菜单与桌面操作，再决定是否清理备份。

## 构建

安装 **JDK 25、Maven 3.9+、Python 3.12+**。首次构建需要访问依赖仓库；已准备好缓存时可离线构建。普通打包只使用 Python 标准库，素材测试和头颅切片另需 Pillow。

```sh
python tools/build.py
```

Linux 可将 `python` 换成 `python3`。脚本依次清理旧输出、准备内置资源包、编译并运行 Java 测试，再核对 JAR 与 ZIP 的校验值。它优先查找工作区相邻的 Maven／缓存，否则使用 `PATH`；JDK 从 `JAVA_HOME` 或 `PATH` 检测。

也可以逐步构建，每步成功后再执行下一步：

```sh
mvn -B -ntp clean
python tools/package_resource_pack.py
mvn -B -ntp package
```

先生成资源包，再编译插件；Maven 会将 `target/3DGames-resource-pack.zip` 与校验值一起内置到 JAR。只运行 Maven 不会从素材目录生成这个 ZIP。

| 输出 | 用途 |
| --- | --- |
| `target/3DGames-1.0.1.jar` | 安装的插件，已内置默认资源包 |
| `target/3DGames-resource-pack.zip` | 独立客户端资源包，供检查或外部托管 |
| `target/resource-pack-manifest.json` | 资源清单、尺寸与摘要 |
| `target/surefire-reports/` | Java 测试结果 |

资源检查与源码导出：

```sh
python -m unittest discover -s tools/tests -v
python tools/package_source.py
```

源码导出需安装 Git，并在 Git 检出中运行，生成 `target/3DGames-source.zip`。它包含当前源码和未提交的新文件，排除 Git 忽略的构建、日志和运行数据。相同文件在同一 Python 环境中会得到相同资源 ZIP。

构建脚本不部署服务器。仓库没有 GitHub Actions 工作流，推送源码不会自动编译或发布。

## 源码目录

```text
3DGames/
├── src/             插件代码、默认配置、语言和 Java 测试
├── resource-pack/   可编辑资源包、物品注册和源素材
├── tools/           构建脚本、卡牌工具和验证
├── target/          自动生成的 JAR、ZIP 和测试结果
├── pom.xml          Maven 构建配置
├── README.md        英文说明
├── README.zh-CN.md  中文说明
└── LICENSE          代码许可
```

| 目录 | 内容 |
| --- | --- |
| `src/main/java/GoldenEggOVO/TDGames/` | `Core/` 应用服务、`Singleplayer/` 单人、`Multiplayer/` 多人、`Shared/` 共用会话与显示；`src/test/java/` 按同样结构存放测试 |
| Java 的 `Shared/interaction/` 和 `Shared/render/` | 在前者处理玩家输入，`BoardSelection` 匹配起点与合法落点。在后者由 `TableView` 管理几何和各玩家的预选显示 |
| `src/main/resources/` | 插件信息、默认配置、翻译和原版模型数据 |
| `src/main/resources/vanilla-models/` | 原版固定几何，按 `tabletop/`、`shared/` 分类；`index.json` 指向各个模型文件 |
| `resource-pack/assets/` | 客户端模型、物品、贴图和音效；`pack.mcmeta` 为资源包说明 |
| `resource-pack/craftengine/` | 随 JAR 内置并自动安装的默认物品注册 |
| `resource-pack/originals/` | 原始图片、SVG、录音和原版卡牌源图，按 `tabletop/`、`shared/` 分类；不打入客户端 ZIP |
| `resource-pack/licenses/`、`sources.json` | 随包署名，以及素材来源、摘要和许可记录 |
| `tools/*.py` | `build.py` 完整构建、`package_resource_pack.py` 只打资源包、`package_source.py` 导出源码 |
| `tools/native_heads/` | 原版卡牌皮肤切片与可选 MineSkin 纹理生成 |
| `tools/tests/` | Python 素材与构建检查，`client/` 使用 Minecraft 模型解析器，`server/` 运行隔离 Purpur 检查 |

历史交付和运行记录保存在仓库外；源码仓库只保留维护当前版本需要的文件。

### 从哪里看代码

插件代码位于 `src/main/java/GoldenEggOVO/TDGames/`，下表路径均相对于该目录；测试按相同结构分类。

```text
TDGames/
├─ TDGamesPlugin.java   插件总装、事件注册与清理
├─ Core/               游戏协调、命令、共用菜单、资源包与基础服务
├─ Singleplayer/       2048／接龙规则、个人进度与单人菜单
├─ Multiplayer/        多人规则、房间、准备／重连、机器人、经济与多人菜单
└─ Shared/             猜颜色／记忆规则、共用会话、模型与交互
```

| 想修改什么 | 从哪里开始 |
|---|---|
| 启动与配置 | `TDGamesPlugin.java` |
| 命令与 Tab 提示 | `Core/command/` |
| 服务端兼容与调度 | `Core/platform/`；Paper 与 Bukkit Dialog 后端共用菜单定义 |
| 游戏创建与模式路由 | `Core/game/GameFactory.java`、`GameService.java`；名称、顺序和人数在 `Shared/game/GameCatalog.java` |
| 单人会话与进度 | `Singleplayer/SingleplayerService.java`、`Singleplayer/progress/SoloProgressStore.java` |
| 多人准备、重连与存档 | `Multiplayer/MultiplayerService.java`、`Multiplayer/room/RoomStore.java`、`RoundActions.java` |
| 共用会话状态和桌面执行 | `Shared/session/GameSession.java`、`SessionHistory.java`、`SessionRuntime.java` |
| 游戏规则和合法操作 | `Singleplayer/game/<game>/`、`Multiplayer/game/<game>/`、`Shared/game/mastermind/` 和 `Shared/game/memory/` |
| 点击、准星与桌面交互 | `Shared/interaction/TableWorldController.java` |
| 桌边加入房间和打开菜单 | `Shared/interaction/TableAccessController.java` |
| 玩家聚焦视角、碰撞和状态恢复 | `Shared/interaction/TablePlayerController.java` |
| 菜单页面与按钮 | `Core/menu/GameMenus.java` 分派共用页面；`Singleplayer/menu/SoloMenus.java` 和 `Multiplayer/menu/RoomMenus.java` 管理模式动作；`Core/menu/GameMenuWindow.java` 适配窗口 |
| 多人金额输入 | `Multiplayer/menu/PokerRaiseWindow.java`、`StakeWindow.java` |
| 费用、余额和结算提示 | `Multiplayer/menu/RoomEconomyText.java`、`Multiplayer/economy/RoomPaymentLedger.java`；Vault/PAPI 接入在 `Multiplayer/integration/` |
| 棋盘显示与玩家可见范围 | `Shared/render/TableView.java`、`Core/render/DisplayAudience.java`、`Core/render/PlayerViews.java` |
| 棋盘坐标、点击区域与棋子几何 | `Shared/render/TableGeometry.java`、`BoardPieceModels.java` |
| 原版棋盘地图绘制和地图 ID 缓存 | `Shared/render/BoardMapArtwork.java`、`BoardMapStore.java` |
| 卡牌、麻将与骰子布局 | `Shared/render/cards/`、`Shared/render/mahjong/`、`Shared/render/dice/` |
| 共用资源包加载、发送和 CraftEngine 对接 | `Core/pack/ResourcePackService.java`；独立模型目录由 `Core/pack/PackedModels.java` 读取 |
| 资源包模型选择与显示 | `Shared/render/packed/BoardModelKeys.java`、`PackedDisplay.java` |
| 机器人、音效与翻译 | `Multiplayer/bot/`、`Shared/audio/`、`Shared/text/`；玩家文字在 `src/main/resources/languages/` |

通常一次操作会经过：输入 → `Core/game/GameService` 分派模式 → 单人或多人服务校验 → 共用运行时执行规则 → 对应模式保存并更新画面。单人服务管理个人进度，多人服务管理准备、断线等待与结算。两种模式共用玩家占用和桌号分配；座位、准备、离线和离席集合对使用方只读。游戏规则不依赖 Bukkit；显示代码不负责判断出牌是否合法。

共用扑克头颅位于 `Core/render/PlayingCardHeads.java`；原版与资源包观众通过 `ResourcePackState` 查询显示模式。麻将公共牌布局、桌体和原版牌面分别位于 `Shared/render/mahjong/MahjongLayout.java`、`MahjongTableFurniture.java` 和 `MahjongTilePatterns.java`。

`Core/platform/` 封装原生 Dialog、消息、事件和线程调度。`Core/render/PlayerViews.java` 保存玩家快照，`DisplayMovement.java` 处理展示实体移动；`Shared/interaction/TableChunks.java` 管理共享区块票据，`TableWorldController.java` 在区域准备好后执行桌面操作。

`Core/economy/` 提供经济接口和 Vault 适配；`Core/text/Language.java` 加载翻译；`Core/menu/DialogMenus.java` 通过平台适配层生成原生菜单。

`tools/tests/server/run_standalone.py` 检查隔离启动、玩法与重启恢复。`--server-dir` 测试目录需包含命名为 `purpur-2622.jar` 的 Purpur 26.2、已准备的依赖／缓存和已接受的 EULA。`--maven-repo` 指定自定义依赖缓存，默认 `~/.m2/repository`；可选 CraftEngine 参数用于资源模型检查。`run_soak.py` 检查连续对局与实体清理。回执与日志保存在本地生成目录。

`tools/tests/client/run_probe.py <Minecraft目录>` 使用已安装的官方客户端解析器检查模型加载和牌面朝向，默认版本为 26.2，可用 `--version` 选择其他已安装客户端。

原版扑克切片：`python tools/native_heads/build-native-card-heads.py --output target/native-heads`；彩八切片：`python tools/native_heads/build-color-eight-heads.py --output target/color-eight-heads`。只有手动运行 `generate-native-card-heads.py` 并指定本地 `--api-key-file` 时才会访问 MineSkin，普通构建和运行不需要密钥。

自动检查不能替代客户端验收。应在 Minecraft 中检查多人暗手隐私、资源包切换与下载失败、准星和点击范围、骰子移动、音量、重启恢复及帧时间表现。

### 修改原版模型

原版模型由 `vanilla-models/` 中的 JSON 描述，桌子、棋子、骰子、麻将图案和牌背都使用同一种格式。`Core/render/model/` 负责加载、校验和坐标变换；各游戏的 Java 负责选择模型、动态摆放、动画、点击和实体回收。

每个 `blocks` 部件包含 `name`、`material`、`position` 和 `size`。`position` 是部件中心，`size` 是三轴尺寸；可选 `rotation` 按 `[pitch, yaw, roll]` 填写角度，组合顺序为 Y、Z、X。可选 `brightness` 为 0～15，省略时使用所在场景的亮度。`labels` 用于固定文字，其内容仍可由语言文件覆盖。

桌体使用局部方块坐标。棋子坐标按棋格间距缩放；麻将图案的 X、Y 坐标按牌面宽高缩放，深度保持原有层次，图案平面内旋转使用 `roll`。同一个几何文件可在索引中被多个模型引用，修改前先核对引用范围。

`shared/cards/head_textures.json` 和 `tabletop/color_eight/head_textures.json` 是原版头颅牌面的签名纹理数据，不属于几何索引。重新制作这些牌面使用 `tools/native_heads/`。

修改上述内置数据后按[构建](#构建)流程重新安装 JAR。客户端资源包的模型仍在 `resource-pack/assets/` 中，两套模型分别维护；`reload-pack` 不会重新加载 JAR 内的原版几何。

### 修改资源包

模型 JSON 在 `resource-pack/assets/3dgames/models/item/`，物品定义在 `items/`，图片在 `textures/item/`，OGG 音频在 `sounds/`。音效事件由 `assets/3dgames/sounds.json` 管理。新增物品时，物品定义路径须与 CraftEngine 的 `item_model` 一致，其中的 `model.model` 须引用实际模型路径；CraftEngine 注册中的逻辑物品标识须与 `CraftEngineModels.IDS` 一致。修改后运行打包和检查，再编译插件；匹配的 JAR 和 ZIP 一起交付。打包不会重新绘图、缩放或转换素材。

文件名写明游戏、颜色、花色、牌面或部件，例如 `poker_spades_ace`（黑桃 A）、`color_eight_card_blue_2`（彩八蓝色 2）和 `mahjong_characters_1`（一万）。物品定义与模型文件使用相同文件名。稳定的逻辑标识通过 CraftEngine 的 `item_model` 注册对应到文件，打包器以注册表为索引。修改客户端文件名时同步路径引用与注册，逻辑标识、原版模型标识及玩法代码保持不变。

维护源码中的默认素材后，按[构建](#构建)的完整流程重新打包。资源打包步骤生成 `target/3DGames-resource-pack.zip` 并更新校验值，随后 Maven 运行 Java 测试，将默认 ZIP 和校验值一起内置到 `target/3DGames-<版本>.jar`。这是源码交付的构建流程；使用统一包或本地资源模式的服务器换图时，直接修改服务端资源文件夹并执行 `/3dgames reload-pack` 即可。外部模式仍须上传与 JAR 匹配的 ZIP。只修改现有 PNG 不需要调整 CraftEngine 注册。

| 目录或文件 | 自定义方式 |
| --- | --- |
| `assets/`、`pack.mcmeta` | 编辑贴图、模型、物品定义、音效及资源包说明；重命名或移动文件时同步更新引用 |
| `craftengine/` | 修改物品注册时保持逻辑标识及客户端资源路径对应；修改后同步更新服务端 CraftEngine 配置 |
| `originals/tabletop/` | 桌游图案、麻将录音和彩八原版卡牌源图 |
| `originals/shared/` | 共用扑克牌图案、原版扑克源图及通用玩法／菜单录音 |
| `originals/tabletop/native-cards/`、`originals/shared/native-cards/` | 原版头颅卡牌素材；修改 PNG 后还需重新生成签名头颅纹理并编译插件，普通资源包打包不会更新头颅图案 |
| `licenses/`、`sources.json` | 素材署名、来源与摘要记录，替换素材时保持记录准确 |

CraftEngine 物品注册按分类拆到 `craftengine/resources/3dgames/configuration/tabletop/items.yml` 和 `shared/items.yml`，逻辑物品标识与客户端模型路径保持不变。新安装使用这些分类文件；自动安装器会检查已有配置，保留自定义定义，只补充缺失的标识，避免重复注册。

原稿文件名写明内容，例如 `mahjong_characters_1.png`（一万）、`color_eight_blue_draw_one.png`（彩八蓝色加一）、`poker_spades_ace.png`（黑桃 A）和 `wood_place_1.ogg`（木棋子落子音效）。头颅导出工具会将这些名称对应到现有牌面标识与皮肤缓存摘要。来源链接、原始摘要和许可声明保留在 `sources.json`，音频对应关系记录在 `originals/shared/sounds/sound_events.json`。修改原稿不会自动更新 `assets/` 中的成品。

JSON 使用两空格缩进，方便直接编辑。

图片名称按游戏和部位命名，例如 `poker_spades_9_face.png`（黑桃 9 牌面）、`color_eight_yellow_6_face.png`（彩八黄色 6 牌面）、`xiangqi_red_pawn_face.png`（红方兵）、`mahjong_table_cloth.png`（麻将桌布）。`top`／`bottom` 表示上面／底面，`front`／`rear` 区分正反面的贴图，`shared_` 表示多个模型共用的图片。

`textures/item/` 下分为 `tabletop/<游戏>/` 和 `shared/`。桌游目录包含 `mahjong/`（麻将）、`xiangqi/`（象棋）、`chess/`（国际象棋）、`connect_four/`（四子棋）、`gomoku/`（五子棋）、`go/`（围棋）、`reversi/`（黑白棋）、`checkers/`（中国跳棋）、`draughts/`（西洋跳棋）、`ludo/`、`color_eight/`（彩八）、`doudizhu/`（斗地主）、`liars_bar/`（骗子酒馆）、`texas_holdem/`（德州扑克）和 `yacht/`（快艇骰子）。每个目录保留该游戏专用的图片；共用图片集中存放一份：

| 共用目录 | 内容 |
| --- | --- |
| `shared/materials/` | 木材、牌体和混凝土等基础材质 |
| `shared/playing_cards/` | 各扑克牌游戏共用的牌面和牌背 |
| `shared/table_parts/` | 多种桌子共用的桌底与桌柱材质 |
| `shared/pieces/` | 围棋／五子棋共用棋子，以及多个棋类共用的金色装饰 |
| `shared/dice/` | Ludo／快艇骰子共用的六面骰子贴图 |

模型和物品定义采用相同分类，在 `models/item/` 和 `items/` 下统一使用 `tabletop/` 和 `shared/`，专用文件继续按游戏分目录。共用扑克牌在 `shared/playing_cards/`，围棋／五子棋共用棋子在 `shared/pieces/`，Ludo／快艇骰子共用骰子在 `shared/dice/`。例如，`items/tabletop/mahjong/mahjong_characters_1.json` 的客户端标识是 `3dgames:tabletop/mahjong/mahjong_characters_1`，由 CraftEngine 的 `item_model` 引用；其中的 `model.model` 引用 `3dgames:item/tabletop/mahjong/mahjong_characters_1`。Java 代码和 CraftEngine 的自定义物品键仍使用逻辑标识 `3dgames:mahjong_m1`。声音事件统一在 `assets/3dgames/sounds.json` 中注册。已有自定义资源包和 CraftEngine 注册采用此布局时，需要同步对应的客户端路径。

资源路径、CraftEngine 物品键、音效、世界实体标签、玩家偏好和原版几何引用统一使用小写命名空间 `3dgames`，插件显示名称仍为 `3DGames`。

扑克牌图案位于 `textures/item/shared/playing_cards/`，55 张圆角牌模型（含大小王）位于 `models/item/shared/playing_cards/`；物品定义在 `items/` 下使用相同路径。

## 许可

整合后的插件采用 [GPL-3.0-or-later](LICENSE)。对应源码可在本仓库及构建工具生成的 `3DGames-source.zip` 中获取。

### 第三方代码与素材

原始许可声明和准确的代码来源保存在 [`src/main/resources/META-INF/`](src/main/resources/META-INF/)：

- NucleoidMC/Gomoku — MIT。
- jwang541/Xiangqi — MIT。
- hongsenliu/chinese-checkers — GPL-3.0-or-later。
- bhlangonijr/chesslib — Apache-2.0。
- Gson — Apache-2.0，运行时库已包含在打包后的 JAR 中。
- Adventure API、MiniMessage 与文本序列化库 — MIT；内置于 JAR，供 Spigot 和 CraftBukkit 使用。原始声明保留在 `META-INF/licenses/adventure-MIT.txt`。
- Kyori Option — MIT；内置的 Adventure 依赖。原始声明保留在 `META-INF/licenses/option-MIT.txt`。

`Core/text/MessageText.java` 和 `Core/text/LabelLayout.java` 改编自 GoldenEggOVO/3DCasinoGames 的 `a4ddd20` 修订版，沿用其 GPL-3.0 许可。本插件的文字排版会保留所有状态行。这些工具的源码已内置，不要求安装其他插件。

服务端 API 和 JOML 由所选服务端提供；Paper 专用入口隔离加载。私人字体、运行配置、世界数据以及第三方服务端或插件 JAR 不包含在本仓库交付中。

内置的 chesslib 子集移除了未使用的事件观察器、废弃 FEN 别名和 Polyglot 开局库哈希；标准走子验证与重复局面检测仍保留。原始 Apache-2.0 声明和代码来源也保留。内置源码位于 `Multiplayer/game/chess/engine/`、`Multiplayer/game/xiangqi/engine/` 和 `Multiplayer/game/chinesecheckers/engine/`。

资源包使用 [lietxia/mahjong_graphic](https://github.com/lietxia/mahjong_graphic) 的透明麻将牌图案，修订版为 `3e275804ff58325306710bef3a7406860444bc6a`，采用允许修改和商业使用的 M+ fonts 许可。选用的 PNG 和 SVG 源文件保存在 `resource-pack/originals/tabletop/textures/mahjong/tiles/`；完整声明和文件摘要集中记录于 `resource-pack/sources.json`，客户端资源包也包含声明。

麻将桌布为本项目原创的纯色绿色桌布，SVG 源文件保存在 `resource-pack/originals/tabletop/textures/mahjong/`。麻将音效采用 Kenney 的 CC0-1.0 素材，准确来源、许可和 SHA-256 摘要记录于 `resource-pack/sources.json`，客户端资源包包含声明。Color Eight 图案和模型几何由本项目制作。CraftEngine 为可选组件，其二进制文件不随本项目分发。

资源包的象棋棋子采用用户选定的 Wikimedia Commons 繁体字 SVG，作者为 Inductiveload，现用矢量修订由 Wj654cj86 提供，已释出至公有领域。每张 SVG 选用第一枚红棋和第三枚黑棋。原始 SVG 与本地渲染的 PNG 保存在 `resource-pack/originals/tabletop/textures/xiangqi/`；准确摘要和文件页面链接记录于 `resource-pack/sources.json`。资源包扑克牌面和蓝色牌背使用 George Blackwell 的 [Pixel Playing Cards Pack](https://georgeblackwell.itch.io/playing-cards-sprite-pack)，采用 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 许可。原始 60×84 PNG 保存在 `resource-pack/originals/shared/textures/playing-cards/`。牌面保持原有宽高比，不增加空白，通过最近邻方式放大两倍；蓝色牌背简化为纯色底和象牙白边框；大王角标文字改为红色。作者署名、许可链接及上述改动记录于 `resource-pack/sources.json` 和客户端资源包声明。原版头颅纹理和源图片内容未改变；资源包每张牌使用一个模型显示实体。

当前共用玩法音效（包括出牌、黑白棋翻面和骰子）使用基于 Kenney CC0 录音制作的实物音效与混音；确认采用的母带位于 `resource-pack/originals/shared/sounds/approved/`，对应事件、处理方式和成品摘要记录在 `resource-pack/sources.json` 的 `approved_audio` 中。其他 Kenney 原始录音按用途保存在 `originals/shared/sounds/`，麻将录音在 `originals/tabletop/sounds/mahjong/`；客户端包保留许可声明。原稿目录另保留用户提供的 `oxidvideos-taking-playing-card-522520.mp3`，其原始下载页面与许可凭证未提供，来源状态仍记录在 `sources.json`，它不是当前出牌事件采用的母带。

原版扑克使用从用户的 Minecraft 26.2 客户端 JAR（`assets/minecraft/textures/entity/`）中裁切的僵尸、骷髅和苦力怕头部纹理。卡牌布局、花色和凋零像素图案由本项目制作。Minecraft 纹理归 Mojang/Microsoft 所有，与 GPL 插件代码分别处理。离线 PNG 源文件仅导入头部正面像素，原版渲染所需的带签名皮肤属性已内置。API Key 和生成缓存不包含在交付中。

将棋与五将棋共用用户选定的 [Kadagaden 双字红木棋子](https://github.com/Kadagaden/shogi-pieces/tree/3a7643b8f6aa9910b360b61e062961e70213c6cc/2-kanji_red_wood)。Kadagaden 的美术采用 CC BY 4.0；双字字形由 Hari Seldon 制作，采用 CC BY-SA 3.0。15 张原始 SVG 和摘要保存在 `resource-pack/originals/tabletop/shogi/`。资源包以 128×128 透明图像呈现原稿，保留奶白字与红木色，每颗棋子使用一个封闭立体模型。来源、许可链接及转换说明记录于 `resource-pack/sources.json` 和 `resource-pack/licenses/shogi-art.txt`。

共用方块材质 PNG 来自 Minecraft 26.2 客户端，仍属于 Mojang/Microsoft 素材，不采用插件代码的 GPL 许可；来源与摘要记录在 `resource-pack/sources.json`。

#### 公开规则参考

以下资料用于参考独立实现的游戏规则。地区规则采用游戏内说明中列出的选项和自定义规则。

- [RIF 规则 9.2–9.3](https://gomoku.renju.net/rifrules/)
- [Switch Ludo 指南](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/ludo)
- [Nintendo 更新说明](https://en-americas-support.nintendo.com/app/answers/detail/a_id/49554/p/989)
- [中国跳棋指南](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/chinese-checkers)
- [Blazing 8s FAQ](https://support-apps.discord.com/hc/en-us/articles/26501925147415-Blazing-8s-FAQ)
- [Nintendo 游戏目录](https://www.nintendo.com/jp/switch/as7ta/games/index.html)
- [EMA 2025 日麻规则](https://mahjong-europe.org/portal/images/docs/Riichi-rules-2025-EN.pdf)
- [四川省体育场馆协会规则](https://www.ssva.org.cn/upload/file/2025-08-22/6389146967009551555075859.pdf)
- [麻将术语参考](https://new.mjonline.com.tw/aboutgame_d.html)
