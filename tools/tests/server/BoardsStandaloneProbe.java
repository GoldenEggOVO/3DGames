package GoldenEggOVO.TDGames.Core.probe;

import net.kyori.adventure.text.Component;

import GoldenEggOVO.TDGames.Shared.interaction.BoardSelection;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;

import GoldenEggOVO.TDGames.Core.internal.gson.*;
import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.HandGame;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.render.classic.ClassicTable;
import GoldenEggOVO.TDGames.Shared.render.classic.ClassicLayout;
import GoldenEggOVO.TDGames.TDGamesPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static GoldenEggOVO.TDGames.Core.text.MessageText.plain;

/** Test-only plugin for an isolated, loopback Purpur server. */
public final class BoardsStandaloneProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::runProbe, 80);
    }

    private void runProbe() {
        try {
            require(Bukkit.getIp().equals("127.0.0.1") && Bukkit.getPort() == 25617, "loopback fixture");
            for (String absent : List.of("ServerGames", "ServerMenu", "ServerCasino", "KaMenu"))
                require(Bukkit.getPluginManager().getPlugin(absent) == null, "unexpected " + absent);
            Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3DGames"));
            require(boards.isEnabled(), "Boards enabled");
            World world = Bukkit.getWorlds().getFirst();
            pictureMenuProbe(boards,world);
            if(Boolean.getBoolean("boards.probe.craftengine")){craftEngineProbe(boards,world);}
            else classicProbe((TDGamesPlugin)boards,world,false);
            if (Boolean.getBoolean("boards.probe.snapshot")) {
                snapshotProbe(boards);
                return;
            }
            soundProbe(boards, world);
            spectralProbe((TDGamesPlugin) boards, world);
            modelProbe(boards, world);
            focusProbe(boards, world);
            handModelProbe(boards, world);
            playingCardProbe(boards, world);
            modeLifecycleProbe((TDGamesPlugin) boards, world);
            PluginCommand command = Objects.requireNonNull(Bukkit.getPluginCommand("3dgames:3dgames"));
            AtomicInteger dialogs = new AtomicInteger();
            Player player = player(world, dialogs, true);
            Player denied = player(world, dialogs, false);
            Collection<String> suggestions = new LinkedHashSet<>(List.of("3dgames", "3dgames:3dgames"));
            Bukkit.getPluginManager().callEvent(new PlayerCommandSendEvent(player, suggestions));
            require(suggestions.equals(Set.of("3dgames")), "duplicate root suggestion hidden");
            int before = dialogs.get();
            command.execute(denied, "3dgames", new String[0]);
            require(dialogs.get() == before, "permission denied");
            command.execute(player, "3dgames", new String[0]);
            require(dialogs.get() == before + 1, "native catalog opened");
            Object menus = field(boards, "menus");
            pokerRaiseProbe(boards, menus, world);
            menuExperienceProbe(boards,menus,player);
            command.execute(player, "3dgames", new String[0]);
            Map<?, ?> sessions = (Map<?, ?>) field(menus, "sessions");
            Object session = sessions.get(player.getUniqueId());
            require(session != null, "menu session exists");
            Method buttons = session.getClass().getDeclaredMethod("buttons");
            buttons.setAccessible(true);
            List<?> entries = (List<?>) buttons.invoke(session);
            Method token = session.getClass().getDeclaredMethod("token");
            token.setAccessible(true);
            String action = "3dgames:" + token.invoke(session) + " 0";
            int shown = dialogs.get();
            call(menus, "handle", new Class<?>[]{Player.class, String.class}, player, action);
            require(dialogs.get() == shown + 1, "menu callback opened next native dialog");
            call(menus, "handle", new Class<?>[]{Player.class, String.class}, player, action);
            require(dialogs.get() == shown + 1, "callback consumed once");
            require(!entries.isEmpty(), "catalog entries");

            Map<?, ?> rooms = (Map<?, ?>) field(field(call(boards,"games",new Class<?>[0]),"runtime"),"sessions");
            Path marker = getDataFolder().toPath().resolve("created-room.txt");
            if (Files.exists(marker)) {
                require(rooms.size() == 1, "one room restored");
                Object room = rooms.values().iterator().next();
                require(field(room, "anchorWorld").equals(world.getUID()), "world anchor restored");
                require(field(room, "board") != null, "board replay restored");
                require(Files.readString(boards.getDataFolder().toPath().resolve("rooms.json")).contains("drop:3"), "move history retained");
                require(!world.getEntitiesByClass(TextDisplay.class).isEmpty(), "board entities rendered");
                String markerName = "BOARDS_STANDALONE_RESTORE_PASS";
                getLogger().info(markerName + " rooms=1 dialogs=" + dialogs.get());
            } else {
                require(rooms.isEmpty(), "fresh fixture");
                call(call(boards,"games",new Class<?>[0]),"create", new Class<?>[]{Player.class, String.class, int.class}, player, "connectfour", 2);
                require(rooms.size() == 1, "room created");
                Object room = rooms.values().iterator().next();
                call(call(boards,"games",new Class<?>[0]),"startWithBots", new Class<?>[]{Player.class, room.getClass()}, player, room);
                call(menus,"room",new Class<?>[]{Player.class,room.getClass()},player,room);
                require(!clickMenu(menus,player,"id","controls"),"room omits the full move selector");
                call(field(boards,"tableWorld"),"pickCell",new Class<?>[]{Player.class,room.getClass(),int.class,String.class},player,room,0,"3,0");
                Path data = boards.getDataFolder().toPath().resolve("rooms.json");
                require(Files.readString(data).contains("drop:3"), "core rule action persisted");
                require(!world.getEntitiesByClass(TextDisplay.class).isEmpty(), "board entities rendered");
                Files.createDirectories(getDataFolder().toPath());
                Files.writeString(marker, String.valueOf(field(room, "id")));
                getLogger().info("BOARDS_STANDALONE_CREATE_PASS rooms=1 dialogs=" + dialogs.get());
            }
        } catch (Throwable ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "BOARDS_STANDALONE_PROBE_FAIL", ex);
        }
    }

    /** Real world/services/files; automatic rules run separately from disconnected proxy policy. */
    @SuppressWarnings("unchecked")
    private void modeLifecycleProbe(TDGamesPlugin plugin, World world) throws Exception {
        var games = plugin.games();
        Map<UUID, GameSession> registry = (Map<UUID, GameSession>) field(field(games, "runtime"), "sessions");
        UUID soloId = UUID.fromString("c728dc4a-0958-40a2-ad4c-d2475df3c071");
        Player soloPlayer = player(world, new AtomicInteger(), true, soloId, new Location(world, 32.5, 83, 32.5));
        Player multiplayerPlayer = player(world, new AtomicInteger(), true, UUID.randomUUID(), new Location(world, 16.5, 83, 32.5));
        Path progress = plugin.getDataFolder().toPath().resolve("solo").resolve(soloId + ".memory.json");
        Path expectedFile = getDataFolder().toPath().resolve("solo-expected.json");
        JsonObject expected = Files.exists(expectedFile)
                ? JsonParser.parseString(Files.readString(expectedFile)).getAsJsonObject() : null;
        require(games.session(soloId) == null, "solo progress does not create an active table during startup");
        GameSession blocker = null, multiplayer = null, solo = null;
        try {
            games.create(multiplayerPlayer, "connectfour", 2);
            multiplayer = games.session(multiplayerPlayer);
            require(multiplayer != null && multiplayer.phase() == GameSession.Phase.LOBBY,
                    "multiplayer creation waits for preparation");
            if (expected != null) {
                int previousTable = expected.get("table").getAsInt();
                if (registry.values().stream().noneMatch(session -> session.table == previousTable)) {
                    blocker = new GameSession(UUID.randomUUID(), "chess", 2, 1L, previousTable);
                    registry.put(blocker.id, blocker);
                }
                plugin.singleplayer().resume(soloPlayer);
            } else games.create(soloPlayer, "memory", 1);
            solo = games.session(soloPlayer);
            require(solo != null && solo.phase() == GameSession.Phase.PLAYING, "solo creation starts directly");
            require(solo.table != multiplayer.table, "singleplayer and multiplayer share exclusive table numbers");
            require(solo.stake() == null && solo.ready.isEmpty() && solo.offline.isEmpty(),
                    "solo start has no payment, readiness or reconnect state");
            if (expected != null) {
                require(!solo.id.toString().equals(expected.get("id").getAsString()), "resume allocates a new session UUID");
                require(solo.table != expected.get("table").getAsInt(), "resume allocates an available table instead of restoring an occupied number");
                require(solo.board().cells().toString().equals(expected.get("cells").getAsString()), "solo board restored from saved actions");
                require(new Gson().toJsonTree(((ClassicGame) solo.board()).publicInfo()).equals(expected.get("publicInfo")),
                        "solo counters and rule state restored from saved actions");
                require(solo.history.size() == expected.get("history").getAsInt(), "solo action history restored");
            } else {
                ClassicGame memory = (ClassicGame) solo.board();
                int[] cards = (int[]) field(solo.board(), "cards");
                int mismatch = 1;
                while (cards[mismatch] == cards[0]) mismatch++;
                games.apply(solo, 0, new JsonPrimitive("flip:m0"), soloPlayer);
                games.apply(solo, 0, new JsonPrimitive("flip:m" + mismatch), soloPlayer);
                require("hide".equals(memory.automaticAction()), "solo Memory mismatch schedules automatic hide");
                require(games.runtime().automatic(solo, solo.changed() + 1201), "shared runtime handles solo automatic rule");
                require(memory.automaticAction() == null && solo.history.size() == 3,
                        "Memory hide executes and records exactly one automatic action");
            }
            Player browser = player(world, new AtomicInteger(), true, UUID.randomUUID());
            Object menus = field(plugin, "menus");
            call(menus, "main", new Class<?>[]{Player.class}, browser);
            require(clickMenu(menus, browser, "id", "rooms"), "multiplayer browser opens with solo active");
            boolean multiplayerListed = false;
            for (int page = 0; page < 10; page++) {
                Object menu = ((Map<?, ?>) field(menus, "sessions")).get(browser.getUniqueId());
                for (Object button : (List<?>) call(menu, "buttons", new Class<?>[0])) {
                    String id = (String) call(button, "id", new Class<?>[0]);
                    require(!id.equals("room-" + solo.id), "multiplayer browser excludes solo sessions");
                    if (id.equals("room-" + multiplayer.id)) multiplayerListed = true;
                }
                if (!clickMenu(menus, browser, "id", "next")) break;
            }
            require(multiplayerListed, "multiplayer browser retains multiplayer rooms");
            call(menus, "forget", new Class<?>[]{Player.class}, browser);
            JsonObject next = new JsonObject();
            next.addProperty("id", solo.id.toString());
            next.addProperty("table", solo.table);
            next.addProperty("cells", solo.board().cells().toString());
            next.add("publicInfo", new Gson().toJsonTree(((ClassicGame) solo.board()).publicInfo()));
            next.addProperty("history", solo.history.size());
            plugin.singleplayer().saveAndLeave(soloPlayer);
            require(games.session(soloId) == null && Files.isRegularFile(progress), "save and leave persists personal progress and releases session");
            JsonObject saved = JsonParser.parseString(Files.readString(progress)).getAsJsonObject();
            require(saved.get("schema").getAsInt() == 1 && saved.get("rulesVersion").getAsInt() == 1
                            && saved.keySet().equals(Set.of("schema", "rulesVersion", "kind", "seed", "options", "history"))
                            && saved.get("history").getAsJsonArray().size() == next.get("history").getAsInt(),
                    "personal progress keeps the existing schema and complete history");
            require(!Files.readString(plugin.getDataFolder().toPath().resolve("rooms.json")).contains(solo.id.toString()),
                    "multiplayer persistence excludes solo sessions");
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(expectedFile, new GsonBuilder().setPrettyPrinting().create().toJson(next));
            getLogger().info("BOARDS_MODE_SPLIT_" + (expected == null ? "CREATE" : "RESTORE")
                    + "_PASS solo_file=true shared_table_allocation=true browser_excludes_solo=true memory_hide=true proxy_player=true automatic_rule_isolated=true");
        } finally {
            if (solo != null && games.session(soloId) != null) games.remove(solo);
            if (multiplayer != null) games.remove(multiplayer);
            if (blocker != null) registry.remove(blocker.id);
            games.save();
        }
    }

    /** Uses the real event bus and world with a stateful player proxy, not a connected player. */
    @SuppressWarnings("unchecked")
    private void focusProbe(Plugin plugin,World world) throws Exception {
        Class<?> roomType=Class.forName("GoldenEggOVO.TDGames.Shared.session.GameSession",true,plugin.getClass().getClassLoader());
        var constructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);constructor.setAccessible(true);
        UUID id=UUID.randomUUID(),roomId=UUID.randomUUID();Object room=constructor.newInstance(roomId,"mahjong",4,1L,999);
        Object tableWorld=field(plugin,"tableWorld"),tablePlayers=field(plugin,"tablePlayers");Map<UUID,Object> rooms=(Map<UUID,Object>)field(field(call(plugin,"games",new Class<?>[0]),"runtime"),"sessions");
        call(room,"join",new Class<?>[]{UUID.class,String.class},id,"FocusProbe");
        call(tableWorld,"anchor",new Class<?>[]{roomType,Location.class},room,new Location(world,32,83,0));rooms.put(roomId,room);
        Location original=new Location(world,32,83,2.25,33,21);
        var location=new java.util.concurrent.atomic.AtomicReference<>(original.clone());
        boolean[] gravity={true},collision={true},cancelReturn={false},invisible={false};
        var mode=new java.util.concurrent.atomic.AtomicReference<>(org.bukkit.GameMode.SURVIVAL);
        var camera=new java.util.concurrent.atomic.AtomicReference<org.bukkit.entity.Entity>();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getUniqueId"->id;case "getName"->"FocusProbe";case "getWorld"->location.get().getWorld();
            case "getLocation"->location.get().clone();case "getY"->location.get().getY();case "getGameMode"->mode.get();
            case "getEyeHeight"->1.62d;
            case "setGameMode"->{mode.set((org.bukkit.GameMode)args[0]);yield null;}
            case "getAllowFlight","isFlying"->false;
            case "getPersistentDataContainer"->world.getPersistentDataContainer();
            case "getSpectatorTarget"->camera.get();
            case "setSpectatorTarget"->{camera.set((org.bukkit.entity.Entity)args[0]);yield null;}
            case "isOnline","isValid","hasPermission","isPermissionSet"->true;
            case "getEffectivePermissions"->java.util.Set.of();
            case "isDead","isInsideVehicle","isOp"->false;
            case "hasGravity"->gravity[0];case "setGravity"->{gravity[0]=(boolean)args[0];yield null;}
            case "isCollidable"->collision[0];case "setCollidable"->{collision[0]=(boolean)args[0];yield null;}
            case "isInvisible"->invisible[0];case "setInvisible"->{invisible[0]=(boolean)args[0];yield null;}
            case "teleport"->{
                Location to=((Location)args[0]).clone();
                var event=new org.bukkit.event.player.PlayerTeleportEvent((Player)proxy,location.get().clone(),to,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                if(cancelReturn[0]&&to.equals(original))event.setCancelled(true);
                Bukkit.getPluginManager().callEvent(event);if(!event.isCancelled())location.set(event.getTo().clone());yield !event.isCancelled();
            }
            case "hashCode"->id.hashCode();case "equals"->proxy==args[0];case "toString"->"FocusProbe";
            default->null;
        });
        java.util.function.Consumer<Boolean> input=held->{
            org.bukkit.Input keys=(org.bukkit.Input)Proxy.newProxyInstance(org.bukkit.Input.class.getClassLoader(),new Class<?>[]{org.bukkit.Input.class},
                (proxy,method,args)->method.getName().equals("isSprint")&&held);
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInputEvent(player,keys));
        };
        try {
            input.accept(true);require((boolean)call(tablePlayers,"hasTableFocus",new Class<?>[]{Player.class},player),"real event registration enters focus");
            require(!gravity[0]&&!collision[0]&&invisible[0]&&Math.abs(location.get().getX()-32)<.0001&&Math.abs(location.get().getY()-85.15)<.0001&&Math.abs(location.get().getZ()-.65)<.0001,"focus owns gravity, collision and invisibility at close position");
            require(mode.get()==org.bukkit.GameMode.SPECTATOR&&camera.get() instanceof org.bukkit.entity.ArmorStand stand&&stand.isMarker()&&!stand.isVisible()&&!stand.isVisibleByDefault()&&stand.getEyeHeight()==0,"fixed private marker camera replaces the player and equipment view");
            org.bukkit.entity.Entity target=camera.get();
            Location anchor=location.get().clone(),attempt=anchor.clone().add(3,1,3);attempt.setYaw(70);
            var move=new org.bukkit.event.player.PlayerMoveEvent(player,anchor,attempt);Bukkit.getPluginManager().callEvent(move);
            getLogger().info("BOARDS_FOCUS_CAMERA cancelled="+move.isCancelled()+" anchored="+location.get().equals(anchor)+" anchor_yaw="+anchor.getYaw()+" camera_yaw="+target.getLocation().getYaw()+" anchor_pitch="+anchor.getPitch()+" camera_pitch="+target.getLocation().getPitch());
            require(move.isCancelled()&&location.get().equals(anchor)&&Math.abs(Math.IEEEremainder(target.getLocation().getYaw()-anchor.getYaw(),360))<.0001&&Math.abs(target.getLocation().getPitch()-anchor.getPitch())<.0001,"real event pipeline cancels movement and aim without a correction plugin teleport");
            var remote=new org.bukkit.event.player.PlayerTeleportEvent(player,anchor,anchor.clone().add(100,0,100),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE);
            Bukkit.getPluginManager().callEvent(remote);require(remote.isCancelled()&&(boolean)call(tablePlayers,"hasTableFocus",new Class<?>[]{Player.class},player),"spectator menu cannot grant remote teleport");
            var detach=new com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent(player,target);Bukkit.getPluginManager().callEvent(detach);
            require(detach.isCancelled(),"held sprint cannot detach the camera");
            input.accept(false);require(location.get().equals(original)&&gravity[0]&&!invisible[0]&&mode.get()==org.bukkit.GameMode.SURVIVAL&&camera.get()==null&&!target.isValid(),"input release restores mode, pose, gravity, visibility and removes camera");
            input.accept(true);Location external=new Location(world,50,83,0);player.teleport(external);input.accept(true);input.accept(false);
            require(location.get().equals(external)&&gravity[0]&&collision[0],"external teleport wins without focus reentry");
            location.set(original.clone());input.accept(true);cancelReturn[0]=true;input.accept(false);
            require(!(boolean)call(tablePlayers,"hasTableFocus",new Class<?>[]{Player.class},player)&&gravity[0]&&!invisible[0]&&Math.abs(location.get().getY()-85.15)<.0001,"cancelled return clears focus flags without overriding cancellation");
            getLogger().info("BOARDS_FOCUS_EVENTS_PASS proxy_player=true real_event_bus=true real_world_clearance=true native_player_physics=false client_visual_test=false");
        } finally {
            cancelReturn[0]=false;input.accept(false);call(tablePlayers,"releaseFocus",new Class<?>[]{Player.class},player);rooms.remove(roomId);
            call(tablePlayers,"syncPlayerState",new Class<?>[]{Player.class},player);
        }
    }

    /** Real Paper visibility metadata: no connected client or screenshot is implied. */
    private void handModelProbe(Plugin plugin,World world) throws Exception {
        ClassLoader loader=plugin.getClass().getClassLoader();Class<?> roomType=Class.forName("GoldenEggOVO.TDGames.Shared.session.GameSession",true,loader);
        Class<?> viewType=Class.forName("GoldenEggOVO.TDGames.Shared.render.TableView",true,loader);
        var roomConstructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class,Map.class);roomConstructor.setAccessible(true);
        var constructor=viewType.getDeclaredConstructors()[0];constructor.setAccessible(true);
        for(String kind:List.of("color-eight","mahjong")){
            int capacity=kind.equals("mahjong")?4:5;Map<String,String> options=kind.equals("mahjong")?Map.of("profile","taiwan"):Map.of();
            Object room=roomConstructor.newInstance(UUID.randomUUID(),kind,capacity,1L,0,options);
            Field board=roomType.getDeclaredField("board");board.setAccessible(true);board.set(room,GameFactory.create(kind,capacity,1L,options));
            Player owner=player(world,new AtomicInteger(),true),spectator=player(world,new AtomicInteger(),true,UUID.randomUUID());
            call(room,"join",new Class<?>[]{UUID.class,String.class},owner.getUniqueId(),"PrivateHandProbe");call(room,"fillBots",new Class<?>[0]);
            Field phase=roomType.getDeclaredField("phase");phase.setAccessible(true);
            @SuppressWarnings({"rawtypes","unchecked"}) Object playing=Enum.valueOf((Class)phase.getType(),"PLAYING");phase.set(room,playing);
            Object view=constructor.newInstance(plugin,room,new Location(world,8,83,0),new org.bukkit.NamespacedKey("3dgames","probe-hand"),field(field(plugin,"tableWorld"),"maps"));
            List<org.bukkit.entity.Entity> privateParts=new ArrayList<>();
            try {
                Object hand=field(view,"handTable");Map<?,?> privateViews=(Map<?,?>)field(hand,"privateViews");
                checkBackFacing((Map<?,?>)field(hand,"publicPieces"), capacity);
                checkBackFacing((Map<?,?>)field(hand,"packPieces"), capacity);
                call(view,"cursor",new Class<?>[]{Player.class,BoardSelection.class,String.class},spectator,null,null);
                require(privateViews.isEmpty(),"spectator does not spawn private faces");
                call(view,"cursor",new Class<?>[]{Player.class,BoardSelection.class,String.class},owner,null,null);
                require(privateViews.size()==1,"only requesting owner has a private view");
                Object own=privateViews.get(owner.getUniqueId());
                require(((Map<?,?>)field(own,"pieces")).size()==((HandGame)board.get(room)).handSize(0),"every own tile has a model");
                for(Object piece:((Map<?,?>)field(own,"pieces")).values())for(Object item:(List<?>)field(piece,"parts")){
                    var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                    require(part.isValid()&&!part.isPersistent()&&!part.isVisibleByDefault(),"private face is live, temporary and hidden by default");
                }
                if(kind.equals("color-eight")){
                    var pieces=(Map<?,?>)field(own,"pieces");Object first=pieces.values().iterator().next();
                    for(Object piece:pieces.values()) if(!((Boolean)field(piece,"packed"))) {
                        var parts=(List<?>)field(piece,"parts");
                        require(parts.size()==24,"Color Eight native face uses twenty-four heads");
                        var textures=GoldenEggOVO.TDGames.Shared.render.cards.ColorEightHeads.tiles((String)field(field(piece,"spec"),"face"));
                        for(int tile=0;tile<parts.size();tile++) {
                            Object part=parts.get(tile);
                            var head=(org.bukkit.entity.ItemDisplay)part;
                            require(head.getItemStack().getType()==org.bukkit.Material.PLAYER_HEAD,"Color Eight head material");
                            var meta=(org.bukkit.inventory.meta.SkullMeta)head.getItemStack().getItemMeta();
                            headSkin(meta,textures.get(tile));
                        }
                    }
                    for(Object piece:((Map<?,?>)field(hand,"publicPieces")).values())
                        if((Boolean)field(field(piece,"spec"),"back")) {
                            var parts=(List<?>)field(piece,"parts");
                            require(parts.size()==2&&parts.stream().allMatch(org.bukkit.entity.BlockDisplay.class::isInstance),"Color Eight public back uses two blocks");
                        }
                    var body=(org.bukkit.entity.Entity)((List<?>)field(first,"parts")).getFirst();double y=body.getLocation().getY();
                    String id=String.valueOf(pieces.keySet().iterator().next());
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,id);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    require(Math.abs(body.getLocation().getY()-y-.085)<.0001,"live native private card has a small hover lift");
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,null);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    require(Math.abs(body.getLocation().getY()-y)<.0001,"hover exit restores the same card entity");
                    Object ring=field(hand,"turnRing");List<?> segments=(List<?>)field(ring,"parts");
                    require(segments.size()==24,"native curved turn ring");
                    var segment=(org.bukkit.entity.Entity)call(segments.getFirst(),"entity",new Class<?>[0]);
                    Location before=segment.getLocation();
                    for(int tick=0;tick<10;tick++)call(hand,"tick",new Class<?>[0]);
                    require(segment.getLocation().distanceSquared(before)>.001,"native turn ring rotates");
                }else{
                    var pieces=(Map<?,?>)field(own,"pieces");Object first=pieces.values().iterator().next();
                    var body=(org.bukkit.entity.Entity)((List<?>)field(first,"parts")).getFirst();Location before=body.getLocation();
                    String id=String.valueOf(pieces.keySet().iterator().next());
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,id);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    require(body.isGlowing()&&Math.abs(body.getLocation().getY()-before.getY()-.065)<.0001
                        &&body.getLocation().getX()==before.getX()&&body.getLocation().getZ()==before.getZ(),"mahjong selected tile lifts without horizontal spreading");
                    var remaining=(org.bukkit.entity.TextDisplay)field(own,"remaining");privateParts.add(remaining);
                    require(!remaining.isVisibleByDefault()&&plain(remaining.text()).startsWith("Remaining: "),"native remaining label is owner-only");
                    Object hud=field(hand,"mahjongHud");
                    require(((List<?>)field(hud,"entities")).size()==28,"native Mahjong HUD has bounded entity inventory");
                    var count=(org.bukkit.entity.TextDisplay)((List<?>)field(hud,"counts")).getFirst();
                    require(plain(count.text()).equals(Integer.toString(((HandGame)board.get(room)).deckSize())),"native tabletop count matches drawable wall");
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,null);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    // Native teleport normalizes -0.0 yaw to +0.0; compare numeric pose, not Location.equals bits.
                    Location restored=body.getLocation();
                    require(!body.isGlowing()&&restored.distanceSquared(before)<1e-10
                        &&restored.getYaw()==before.getYaw()&&restored.getPitch()==before.getPitch()&&plain(remaining.text()).isEmpty(),
                        "mahjong hover restores position and clears hint: glowing="+body.isGlowing()+" before="+before+" after="+body.getLocation()+" hint="+plain(remaining.text()));
                    BoardGame game=(BoardGame)board.get(room);boolean offered=false;
                    for(int step=0;step<800&&!game.finished();step++){
                        int seat=game.currentPlayer();List<String> legal=game.legalActions(seat);
                        if(seat==0&&legal.stream().anyMatch(a->a.startsWith("chi:")||a.startsWith("pon:")||a.startsWith("kan-"))){offered=true;break;}
                        require(!legal.isEmpty(),"mahjong fixture can advance");game.apply(seat,legal.getFirst());
                    }
                    require(offered,"real Mahjong game reaches an owner call");
                    Field revision=roomType.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(room,revision.getLong(room)+1);
                    call(hand,"show",new Class<?>[]{Player.class},owner);
                    Map<?,?> calls=(Map<?,?>)field(own,"calls");require(!calls.isEmpty(),"available native Mahjong buttons");
                    for(Object button:calls.values())require(((List<?>)field(button,"parts")).size()==3,"first-stage call buttons contain two surface layers and a label without a tile combination");
                    String group=calls.keySet().stream().map(String::valueOf).filter(key->List.of("chi","pon","kan").contains(key)).findFirst().orElseThrow();
                    require((boolean)call(hand,"expandCall",new Class<?>[]{Player.class,String.class},owner,group),"selecting a call opens its combinations");
                    boolean previews=false;for(Object button:calls.values())if(((List<?>)field(button,"parts")).size()>3)previews=true;
                    require(previews,"second-stage call buttons include native tile-face combination previews");
                    for(Object button:calls.values())for(Object item:(List<?>)field(button,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(part.isValid()&&!part.isPersistent()&&!part.isVisibleByDefault(),"Mahjong button is private and temporary");
                    }
                    HandGame riichi=null;
                    for(long seed=0;seed<50;seed++){
                        HandGame candidate=(HandGame)GameFactory.create("mahjong",4,seed,Map.of("profile","riichi"));
                        int indicator=GoldenEggOVO.TDGames.Multiplayer.game.mahjong.Tiles.type(candidate.publicInfo().get("dora"));
                        int dora=GoldenEggOVO.TDGames.Multiplayer.game.mahjong.Tiles.next(indicator);
                        if(candidate.hand(0).stream().anyMatch(tile->tile.face().endsWith("0")||GoldenEggOVO.TDGames.Multiplayer.game.mahjong.Tiles.type(tile.face())==dora)){riichi=candidate;break;}
                    }
                    require(riichi!=null,"Riichi fixture has an owner bonus tile");board.set(room,riichi);
                    revision.setLong(room,revision.getLong(room)+1);call(hand,"sync",new Class<?>[0]);call(hand,"show",new Class<?>[]{Player.class},owner);
                    int glints=0;
                    for(Object piece:((Map<?,?>)field(own,"pieces")).values())for(Object item:(List<?>)field(piece,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(!part.isVisibleByDefault(),"Riichi bonus face remains owner-only");
                        if(part instanceof org.bukkit.entity.ItemDisplay display){
                            require(Boolean.TRUE.equals(display.getItemStack().getItemMeta().getEnchantmentGlintOverride()),"real ItemDisplay preserves native enchanted glint override");
                            require(display.getItemStack().getType()==org.bukkit.Material.SMOOTH_QUARTZ,"glint uses an opaque tile face substrate");glints++;
                        }
                    }
                    require(glints>0,"live owner bonus tile has an ItemDisplay glint");
                    var indicators=(Map<?,?>)field(own,"indicators");require(indicators.size()==5,"owner gets five inset Dora slots");
                    long faces=0;for(Object tile:indicators.values())if(!(boolean)field(field(tile,"spec"),"back"))faces++;
                    require(faces==1,"only the initial Dora indicator is revealed");
                    var assists=(Map<?,?>)field(own,"assistanceButtons");require(assists.size()==4,"owner gets four Riichi assistance controls");
                    for(Object button:assists.values())for(Object item:(List<?>)field(button,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(part.isValid()&&!part.isVisibleByDefault(),"assistance control is private");
                    }
                    var counters=(org.bukkit.entity.Entity)field(own,"counters");privateParts.add(counters);
                    require(counters.isValid()&&!counters.isVisibleByDefault(),"Dora counters are private");
                    require(((Map<?,?>)field(hand,"publicPieces")).keySet().stream().noneMatch(key->key.toString().startsWith("dora:")),"Dora indicator models are not public");
                    for(Object piece:indicators.values())for(Object item:(List<?>)field(piece,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(part.isValid()&&!part.isVisibleByDefault()&&!part.isPersistent(),"inset Dora indicator is private and temporary");
                    }
                    call(hand,"show",new Class<?>[]{Player.class},spectator);require(privateViews.size()==1,"spectator cannot request Dora indicators");
                    getLogger().info("BOARDS_DORA_GLINT_PASS owner_only=true private_indicators=true opaque_foil=true native_item_metadata=true client_visual_test=false");
                }
                call(view,"clear",new Class<?>[]{Player.class},owner);
                require(privateViews.isEmpty()&&privateParts.stream().noneMatch(org.bukkit.entity.Entity::isValid),"private hand removed when view ends");
            } finally {call(view,"close",new Class<?>[0]);}
        }
        getLogger().info("BOARDS_HAND_MODELS_PASS games=color-eight,taiwan,riichi private_by_default=true spectator_faces=0 client_visual_test=false");
    }

    private static void checkBackFacing(Map<?,?> visuals, int seats) throws Exception {
        for (var entry : visuals.entrySet()) {
            String[] key = entry.getKey().toString().split(":");
            if (key.length < 3 || !key[key.length - 3].equals("back")) continue;
            int seat = Integer.parseInt(key[key.length - 2]);
            double expected = -360.0 * seat / seats;
            List<?> parts = (List<?>)field(entry.getValue(), "parts");
            require(!parts.isEmpty(), "public card back has a display");
            for (Object part : parts) {
                Location at = ((org.bukkit.entity.Entity)part).getLocation();
                require(Math.abs(Math.IEEEremainder(at.getYaw() - expected, 360)) < .001
                        && Math.abs(at.getPitch()) < .001, "public card back faces seat " + seat);
            }
        }
    }

    /** Exercise new private hands and real ray targets without persisting synthetic rooms. */
    private void playingCardProbe(Plugin plugin, World world) throws Exception {
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> roomType = Class.forName("GoldenEggOVO.TDGames.Shared.session.GameSession", true, loader);
        Class<?> viewType = Class.forName("GoldenEggOVO.TDGames.Shared.render.TableView", true, loader);
        var roomConstructor = roomType.getDeclaredConstructor(UUID.class, String.class, int.class, long.class, int.class);
        roomConstructor.setAccessible(true);
        var viewConstructor = viewType.getDeclaredConstructors()[0];
        viewConstructor.setAccessible(true);
        for (String kind : List.of("doudizhu", "liars-bar", "texas-holdem")) {
            int capacity = kind.equals("doudizhu") ? 3 : kind.equals("liars-bar") ? 4 : 6;
            Object room = roomConstructor.newInstance(UUID.randomUUID(), kind, capacity, 7L, 0);
            Player owner = player(world, new AtomicInteger(), true);
            Player spectator = player(world, new AtomicInteger(), true, UUID.randomUUID());
            call(room, "join", new Class<?>[]{UUID.class, String.class}, owner.getUniqueId(), "CardProbe");
            call(room, "fillBots", new Class<?>[0]);
            Field board = roomType.getDeclaredField("board"); board.setAccessible(true);
            board.set(room, GameFactory.create(kind, capacity, 7L));
            Field phase = roomType.getDeclaredField("phase"); phase.setAccessible(true);
            @SuppressWarnings({"rawtypes", "unchecked"}) Object playing = Enum.valueOf((Class)phase.getType(), "PLAYING");
            phase.set(room, playing);
            Object view = viewConstructor.newInstance(plugin, room, new Location(world, 8, 83, 0),
                new org.bukkit.NamespacedKey("3dgames", "probe-cards"), field(field(plugin, "tableWorld"), "maps"));
            try {
                Object table = field(view, "playingTable");
                checkBackFacing((Map<?,?>)field(table, "publicCards"), capacity);
                call(table, "show", new Class<?>[]{Player.class}, spectator);
                Map<?, ?> views = (Map<?, ?>)field(table, "privateViews");
                require(views.isEmpty(), "new-game spectator cannot request faces");
                call(table, "show", new Class<?>[]{Player.class}, owner);
                require(views.size() == 1, "new-game owner private view");
                Object own = views.get(owner.getUniqueId());
                Map<?, ?> cards = (Map<?, ?>)field(own, "cards");
                require(cards.size() == ((HandGame)board.get(room)).handSize(0), "new-game complete own hand");
                List<org.bukkit.entity.Entity> privateParts = new ArrayList<>();
                for (Object card : cards.values()) {
                    List<?> parts = (List<?>)field(card, "parts");
                    var textures=GoldenEggOVO.TDGames.Core.render.PlayingCardHeads.tiles((String)field(field(card,"spec"),"face"));
                    boolean nativeHead = parts.getFirst() instanceof org.bukkit.entity.ItemDisplay item
                            && item.getItemStack().getType() == org.bukkit.Material.PLAYER_HEAD;
                    require(parts.size() == (nativeHead ? 24 : 1), "new-game card display budget");
                    int tile=0;
                    for (Object entity : parts) {
                        var part = (org.bukkit.entity.Entity)entity;
                        require(part.isValid() && !part.isPersistent() && !part.isVisibleByDefault(), "new-game face private at spawn");
                        if (nativeHead) {
                            var head = (org.bukkit.entity.ItemDisplay)part;
                            require(head.getItemDisplayTransform() == org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE,
                                    "native head uses the verified NONE transform");
                            var meta = (org.bukkit.inventory.meta.SkullMeta)head.getItemStack().getItemMeta();
                            headSkin(meta,textures.get(tile++));
                        }
                        privateParts.add(part);
                    }
                }
                Object first = cards.values().iterator().next();
                var body = (org.bukkit.entity.Entity)((List<?>)field(first, "parts")).getFirst();
                Location eye = body.getLocation().add(0, .125, 1.5);
                String hit = (String)call(table, "handHit", new Class<?>[]{Player.class, Location.class, org.bukkit.util.Vector.class}, owner, eye, new org.bukkit.util.Vector(0, 0, -1));
                require(String.valueOf(cards.keySet().iterator().next()).equals(hit), "new-game upright card ray target");
                call(view, "clear", new Class<?>[]{Player.class}, owner);
                require(views.isEmpty() && privateParts.stream().noneMatch(org.bukkit.entity.Entity::isValid), "new-game private displays removed");
            } finally { call(view, "close", new Class<?>[0]); }
        }
        getLogger().info("BOARDS_NEW_CARDS_PASS games=doudizhu,liars-bar,texas-holdem private_by_default=true ray_targets=true client_visual_test=false");
    }

    private void craftEngineProbe(Plugin plugin,World world)throws Exception{
        world.getChunkAt(0,0).addPluginChunkTicket(this);
        Plugin ce=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("CraftEngine"));
        require(ce.isEnabled(),"CraftEngine enabled");
        var registration=new HashMap<String,String>();
        try(var paths=Files.walk(ce.getDataFolder().toPath().resolve("resources/3dgames/configuration"))){
            for(Path path:paths.filter(file->file.toString().endsWith(".yml")||file.toString().endsWith(".yaml")).toList()){
                var part=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(path.toFile());
                var items=part.getConfigurationSection("items");
                if(items==null)continue;
                for(String key:items.getKeys(false))
                    require(registration.putIfAbsent(key,items.getString(key+".item_model"))==null,"unique CE item: "+key);
            }
        }
        Class<?> type=Class.forName("GoldenEggOVO.TDGames.Core.pack.CraftEngineModels",true,plugin.getClass().getClassLoader());
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object models=ctor.newInstance();
        require((boolean)call(models,"ready",new Class<?>[0]),"all CE models registered and buildable");
        Field ids=type.getDeclaredField("IDS");ids.setAccessible(true);int count=0;
        for(Object id:(List<?>)ids.get(null)){
            org.bukkit.inventory.ItemStack item=(org.bukkit.inventory.ItemStack)call(models,"item",new Class<?>[]{String.class},id);
            require(item.getType()==org.bukkit.Material.PAPER,"CE carrier");
            var itemModel=org.bukkit.NamespacedKey.fromString(registration.get("3dgames:"+id));
            require(itemModel!=null && itemModel.equals(item.getItemMeta().getItemModel()),"item model component: "+id+" actual="+item.getItemMeta().getItemModel());
            org.bukkit.entity.ItemDisplay display=world.spawn(new Location(world,8,84,0),org.bukkit.entity.ItemDisplay.class,d->{
                d.setVisibleByDefault(false);d.setPersistent(false);d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);d.setItemStack(item);
            });
            require(display.isValid()&&!display.isVisibleByDefault(),"packed display hidden before audience");
            require(display.getItemDisplayTransform()==org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED,"client-readable model display transform");
            display.remove();count++;
        }
        Map<?,?> before = new HashMap<>((Map<?,?>)field(models,"models"));
        Field checked=type.getDeclaredField("checked");checked.setAccessible(true);checked.setLong(models,0);
        require((boolean)call(models,"ready",new Class<?>[0]),"CE models remain ready after registry poll");
        Map<?,?> after=(Map<?,?>)field(models,"models");
        for(var entry:before.entrySet())require(entry.getValue()==after.get(entry.getKey()),"unchanged CE definition reuses built model: "+entry.getKey());
        getLogger().info("BOARDS_CRAFTENGINE_PASS models="+count+" cached_poll=true optional_api=true client_visual_test=false");
        classicProbe((TDGamesPlugin)plugin,world,true);
        Object pack=field(plugin,"pack");
        require(field(pack,"mode").toString().equals("MIXED"),"auto mode selects mixed with CraftEngine");
        call(pack,"close",new Class<?>[0]);
        plugin.getConfig().set("rendering.mode","vanilla");
        Field packField=plugin.getClass().getDeclaredField("pack");packField.setAccessible(true);
        packField.set(plugin,pack.getClass().getConstructor(plugin.getClass()).newInstance(plugin));
        getLogger().info("BOARDS_AUTO_RENDERING_PASS selected=mixed native_geometry_fixture=vanilla config_saved=false");
    }

    /** Supplies synthetic viewers only; every spawn, ray trace and entity lookup delegates to Purpur. */
    private final class ClassicFixture {
        final World real;
        final World world;
        final Location origin;
        final List<Player> players=new ArrayList<>();
        final List<org.bukkit.entity.Entity> spawned=new ArrayList<>();
        final Map<UUID,Set<UUID>> shown=new LinkedHashMap<>();
        final AtomicInteger configuredAtSpawn=new AtomicInteger();

        @SuppressWarnings("unchecked")
        ClassicFixture(World real,boolean managed) {
            this.real=real;
            world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->{
                if(method.getName().equals("getPlayers"))return List.copyOf(players);
                if(method.getName().equals("equals"))return proxy==args[0]||real==args[0];
                if(method.getName().equals("hashCode"))return real.hashCode();
                Object[] forwarded=args==null?null:args.clone();
                if(forwarded!=null)for(int i=0;i<forwarded.length;i++)if(forwarded[i] instanceof Location location){
                    Location at=location.clone();at.setWorld(real);forwarded[i]=at;
                }
                if(method.getName().equals("spawn")&&forwarded!=null)for(int i=0;i<forwarded.length;i++)
                    if(forwarded[i] instanceof java.util.function.Consumer<?> initializer){
                        var configure=(java.util.function.Consumer<org.bukkit.entity.Entity>)initializer;
                        forwarded[i]=(java.util.function.Consumer<org.bukkit.entity.Entity>)entity->{
                            configure.accept(entity);
                            boolean dice=entity.getPersistentDataContainer().getKeys().stream().anyMatch(key->{
                                String value=entity.getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.STRING);
                                return value!=null&&value.endsWith("|@roll");
                            });
                            boolean boardTarget=entity instanceof org.bukkit.entity.Interaction target && target.isResponsive()
                                    && target.getPersistentDataContainer().getKeys().stream().anyMatch(key->{
                                String value=target.getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.STRING);
                                return value!=null&&value.endsWith("|@board");
                            });
                            require(entity.isVisibleByDefault()==(boardTarget||!managed&&dice)&&!entity.isPersistent(),"public click targets, unmanaged dice and private displays have the expected spawn visibility");
                            configuredAtSpawn.incrementAndGet();
                        };
                    }
                try {
                    Object result=method.invoke(real,forwarded);
                    if(method.getName().equals("spawn")&&result instanceof org.bukkit.entity.Entity entity) {
                        spawned.add(entity);
                        Class<?> type=(Class<?>)args[1];
                        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(display,operation,parameters)->{
                            if(operation.getName().equals("equals"))return display==parameters[0];
                            Object[] values=parameters==null?null:parameters.clone();
                            if(operation.getName().startsWith("teleport")&&values!=null&&values[0] instanceof Location location) {
                                Location at=location.clone();at.setWorld(real);values[0]=at;
                            }
                            try{return operation.invoke(entity,values);}
                            catch(java.lang.reflect.InvocationTargetException exception){throw exception.getCause();}
                        });
                    }
                    return result;
                }catch(java.lang.reflect.InvocationTargetException exception){throw exception.getCause();}
            });
            origin=new Location(world,64,83,0);
            for(int i=0;i<3;i++) {
                UUID id=UUID.randomUUID();int number=i;
                shown.put(id,new HashSet<>());
                Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
                    case "getUniqueId"->id;case "getName"->"ClassicProbe"+number;
                    case "getWorld"->world;case "getLocation","getEyeLocation"->origin.clone().add(0,2,2);
                    case "getEyeHeight"->1.62d;
                    case "getPersistentDataContainer"->real.getPersistentDataContainer();
                    case "getServer"->getServer();case "getPose"->org.bukkit.entity.Pose.STANDING;
                    case "isOnline","isValid","hasPermission","isPermissionSet"->true;
                    case "isDead","isOp","isInsideVehicle","isFlying","isSneaking"->false;
                    case "getEffectivePermissions"->Set.of();case "locale"->Locale.US;case "getLocale"->"en_us";
                    case "showEntity"->{shown.get(id).add(((org.bukkit.entity.Entity)args[1]).getUniqueId());yield null;}
                    case "hideEntity"->{shown.get(id).remove(((org.bukkit.entity.Entity)args[1]).getUniqueId());yield null;}
                    case "teleport"->true;
                    case "hashCode"->id.hashCode();case "equals"->proxy==args[0];case "toString"->"ClassicProbe"+number;
                    default->fixtureDefault(method.getReturnType());
                });
                players.add(player);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void classicProbe(TDGamesPlugin plugin,World real,boolean withPack)throws Exception {
        require(GameCatalog.CLASSIC_IDS.size()==13,"exactly thirteen new classic games");
        var preference=new org.bukkit.NamespacedKey("3dgames","resource_pack");
        Byte previousPreference=real.getPersistentDataContainer().get(preference,org.bukkit.persistence.PersistentDataType.BYTE);
        Object pack=plugin.pack();Map<UUID,Object> requests=(Map<UUID,Object>)field(pack,"requests");
        List<org.bukkit.Chunk> retained=new ArrayList<>();int nativeGames=0,packedGames=0,actions=0;
        try {
            for(int x=3;x<=4;x++)for(int z=-1;z<=0;z++) {
                var chunk=real.getChunkAt(x,z);chunk.addPluginChunkTicket(this);retained.add(chunk);
                require(chunk.isLoaded(),"classic fixture chunk loaded");
            }
            for(String kind:GameCatalog.CLASSIC_IDS) {
                ClassicFixture fixture=new ClassicFixture(real,plugin.pack().managed());
                int capacity=Set.of("2048","klondike").contains(kind)?1:kind.equals("monopoly")?3:2;
                GameSession room=new GameSession(UUID.randomUUID(),kind,capacity,17L,999);
                ClassicGame game=(ClassicGame)GameFactory.create(kind,capacity,17L);
                for(int seat=0;seat<Math.min(capacity,2);seat++)room.join(fixture.players.get(seat).getUniqueId(),"ClassicProbe"+seat);
                room.fillBots();setField(room,"board",game);setField(room,"phase",GameSession.Phase.PLAYING);
                if(kind.equals("battleship")) {
                    try(var setup=new ClassicTable(plugin,room,fixture.origin,new org.bukkit.NamespacedKey("3dgames","probe-sea-setup"))) {
                        require("ship:4".equals(setup.action(fixture.players.get(0),"@ship/4")),"physical ship rack selects an existing action");
                        require(setup.action(fixture.players.get(2),"@ship/0")==null,"observer cannot select a private ship");
                        require("place:b0_0".equals(setup.action(fixture.players.get(0),"b0_0")),"physical sea directly places the selected ship");
                        setup.hover(fixture.players.get(0),"b0_0");
                        Object owner=((Map<?,?>)field(setup,"views")).get(fixture.players.get(0).getUniqueId());
                        Map<?,?> colors=(Map<?,?>)field(owner,"highlightColors");
                        for(int position=0;position<5;position++)require(colors.get("b0_"+position)==org.bukkit.Material.LIME_CONCRETE,"whole legal ship footprint has a private preview");
                    }
                    for(int seat=0;seat<2;seat++){classicCommit(room,game,seat,"auto");classicCommit(room,game,seat,"confirm");}
                }
                if(kind.equals("mastermind")) {
                    for(int slot=0;slot<4;slot++){classicCommit(room,game,0,"slot:"+slot);classicCommit(room,game,0,"color:"+slot);}
                    classicCommit(room,game,0,"submit");
                }
                if(kind.equals("nine-mens-morris")||kind.equals("hex"))classicCommit(room,game,0,game.legalActions(0).getFirst());
                if(withPack) {
                    require(plugin.pack().managed(),"packed probe uses real managed resource pack");
                    real.getPersistentDataContainer().set(preference,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
                    fixturePackReady(plugin,fixture.players.get(1));
                    require(plugin.pack().packed(fixture.players.get(1)),"real CE ready state enables packed viewer");
                    require(!plugin.pack().packed(fixture.players.get(0)),"unloaded viewer retains native fallback");
                }
                var tag=new org.bukkit.NamespacedKey("3dgames","probe-classic");
                ClassicTable table=null;
                int nativeCount=0,packedCount=0,dynamicCount=0;
                try {
                    table=new ClassicTable(plugin,room,fixture.origin,tag);
                    List<org.bukkit.entity.Entity> nativeFurniture=(List<org.bukkit.entity.Entity>)field(table,"nativeFurniture");
                    List<org.bukkit.entity.Entity> packedFurniture=(List<org.bukkit.entity.Entity>)field(table,"packedFurniture");
                    require(!nativeFurniture.isEmpty(),kind+" must spawn real native furniture");
                    require(nativeFurniture.stream().anyMatch(org.bukkit.entity.BlockDisplay.class::isInstance),kind+" real native block geometry");
                    nativeCount=nativeFurniture.size();nativeGames++;
                    if(withPack) {
                        require(!packedFurniture.isEmpty(),kind+" must spawn real packed furniture");
                        String model="classic_"+kind.replace('-','_')+"_board";
                        var expected=plugin.pack().item(model).getItemMeta().getItemModel();
                        require(expected!=null,kind+" CE board item has an actual item model component");
                        require(packedFurniture.stream().filter(org.bukkit.entity.ItemDisplay.class::isInstance)
                                .map(org.bukkit.entity.ItemDisplay.class::cast).anyMatch(display->expected.equals(display.getItemStack().getItemMeta().getItemModel())),kind+" real packed board item rendered");
                        packedCount=packedFurniture.size();packedGames++;
                    } else require(packedFurniture.isEmpty(),"native-only run does not claim packed entities");
                    classicCheckViews(table,fixture,room,game);
                    classicPrivacy(table,fixture,kind,game);
                    if(kind.equals("hex"))hexGeometry(table,fixture);
                    if(Set.of("backgammon","monopoly").contains(kind))diceInterpolation(field(table,"diceTray"));
                    Object firstView=((Map<?,?>)field(table,"views")).get(fixture.players.get(0).getUniqueId());
                    ClassicLayout layout=(ClassicLayout)field(firstView,"layout");
                    List<String> controls=(List<String>)field(firstView,"controlActions");
                    if(kind.equals("2048"))require(controls.stream().noneMatch(action->action.startsWith("slide:")),"directional input does not render a legacy button bank");
                    if(kind.equals("mastermind")) {
                        require(controls.stream().noneMatch(action->action.startsWith("color:")||action.startsWith("slot:")),"hole input does not render a legacy color button bank");
                        for(int color=0;color<6;color++)require(("color:"+color).equals(table.action(fixture.players.get(1),"@palette/"+color)),"physical color tray uses the existing color action");
                        require(table.action(fixture.players.get(2),"@palette/0")==null,"observer cannot edit the hidden draft");
                    }
                    for(var slot:layout.slots())classicRay(table,fixture,fixture.players.get(0),slot);
                    var firstSlot=layout.slots().getFirst();
                    var obstruction=real.getBlockAt((int)Math.floor(fixture.origin.getX()+firstSlot.x()),84,(int)Math.floor(fixture.origin.getZ()+firstSlot.z()));
                    var oldBlock=obstruction.getBlockData();
                    try {
                        obstruction.setType(org.bukkit.Material.STONE,false);
                        require(table.hit(fixture.players.get(0),fixture.origin.clone().add(firstSlot.x(),2,firstSlot.z()),new org.bukkit.util.Vector(0,-1,0))==null,kind+" real world block occludes pointer");
                    } finally {obstruction.setBlockData(oldBlock,false);}
                    int initialSpawns=fixture.spawned.size();
                    for(int unchanged=0;unchanged<3;unchanged++)table.sync();
                    require(initialSpawns==fixture.spawned.size(),kind+" unchanged sync retains live displays");
                    Random bot=new Random(31);int committed=0,animatedTicks=0;
                    for(int step=0;step<8&&!game.finished();step++) {
                        int actor=game.currentPlayer();String action=game.automaticAction();
                        if(action==null)action=game.botAction(actor,bot);
                        if(action==null)action=game.legalActions(actor).stream().findFirst().orElse(null);
                        if(kind.equals("monopoly")&&action!=null&&!game.legalActions(actor).contains(action)) {
                            String operation=action.split(":",2)[0]+":";
                            action=game.legalActions(actor).stream().filter(candidate->candidate.startsWith(operation)).findFirst().orElseThrow();
                        }
                        require(action!=null,kind+" has an action to exercise");
                        boolean directInput=kind.equals("2048")&&action.startsWith("slide:")||kind.equals("mastermind")&&action.startsWith("color:");
                        if(game.automaticAction()==null&&actor<2&&actor<capacity&&!directInput)classicClick(table,fixture,fixture.players.get(actor),action,kind);
                        classicCommit(room,game,actor,action);table.sync();committed++;actions++;
                        animatedTicks+=classicSettle(table,game);
                        classicCheckViews(table,fixture,room,game);
                    }
                    require(committed>=3,kind+" executes multiple actual rule actions");
                    Map<?,?> views=(Map<?,?>)field(table,"views");
                    for(Object view:views.values())for(Object visual:((Map<?,?>)field(view,"visuals")).values())dynamicCount+=((List<?>)field(visual,"parts")).size();
                    require(dynamicCount>0,kind+" nonempty real dynamic cell models");
                    if(withPack) {
                        var before=game.cells();var info=game.publicInfo();
                        fixturePackReady(plugin,fixture.players.get(0));
                        requests.remove(fixture.players.get(1).getUniqueId());table.sync();
                        require(game.cells().equals(before)&&game.publicInfo().equals(info),kind+" pack switch preserves rule state");
                        classicCheckViews(table,fixture,room,game);
                    }
                    getLogger().info("BOARDS_CLASSIC_RENDER_PASS game="+kind+" native_furniture="+nativeCount+" packed_furniture="+packedCount
                            +" dynamic_parts="+dynamicCount+" committed_actions="+committed+" animation_ticks="+animatedTicks+" real_world=true proxy_players=true client_visual_test=false");
                } catch(Exception | LinkageError exception) {
                    getLogger().log(java.util.logging.Level.SEVERE,"Classic fixture failed before cleanup: "+kind,exception);
                    throw exception;
                } finally {
                    if(table!=null)table.close();
                    else fixture.spawned.forEach(org.bukkit.entity.Entity::remove);
                    for(Player viewer:fixture.players)requests.remove(viewer.getUniqueId());
                    require(!fixture.spawned.isEmpty(),kind+" fixture cannot pass without spawning real entities");
                    require(fixture.configuredAtSpawn.get()==fixture.spawned.size(),kind+" every real display configured before world tracking");
                    for(var entity:fixture.spawned)require(!entity.isValid(),kind+" close removes every real spawned display: "+entity.getType()+" "+entity.getLocation());
                    require(real.getEntities().stream().noneMatch(entity->(room.id+"|@board").equals(entity.getPersistentDataContainer().get(tag,org.bukkit.persistence.PersistentDataType.STRING))),kind+" close leaves no tagged entities in real world");
                }
            }
            require(nativeGames==13,"all thirteen native classic renderers exercised");
            getLogger().info("BOARDS_CLASSICS_NATIVE_PASS games="+nativeGames+" actions="+actions+" nonempty_models=true real_spawn=true close_zero_residual=true client_visual_test=false");
            if(withPack) {
                require(packedGames==13,"all thirteen real CE classic renderers exercised");
                getLogger().info("BOARDS_CLASSICS_PACKED_PASS games="+packedGames+" synthetic_loaded_status=true real_ce_items=true nonempty_models=true close_zero_residual=true client_visual_test=false");
            }
        } finally {
            for(var chunk:retained)chunk.removePluginChunkTicket(this);
            if(previousPreference==null)real.getPersistentDataContainer().remove(preference);
            else real.getPersistentDataContainer().set(preference,org.bukkit.persistence.PersistentDataType.BYTE,previousPreference);
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void fixturePackReady(TDGamesPlugin plugin,Player viewer)throws Exception {
        Object pack=plugin.pack();ClassLoader loader=pack.getClass().getClassLoader();
        Class<?> stage=Class.forName(pack.getClass().getName()+"$Stage",true,loader);
        Class<?> request=Class.forName(pack.getClass().getName()+"$Request",true,loader);
        var constructor=request.getDeclaredConstructor(UUID.class,stage);constructor.setAccessible(true);
        UUID requestId=UUID.randomUUID();
        ((Map<UUID,Object>)field(pack,"requests")).put(viewer.getUniqueId(),constructor.newInstance(requestId,Enum.valueOf((Class)stage,"LOADING")));
        plugin.pack().status(viewer,requestId,org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    private void headSkin(org.bukkit.inventory.meta.SkullMeta meta,GoldenEggOVO.TDGames.Core.render.PlayingCardHeads.Texture texture)throws Exception {
        String json=new String(Base64.getDecoder().decode(texture.value()),java.nio.charset.StandardCharsets.UTF_8);
        var expected=java.net.URI.create(JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                .getAsJsonObject("SKIN").get("url").getAsString());
        var profile=meta.getOwnerProfile();
        require(profile!=null&&profile.getTextures().getSkin()!=null,"native head retains a public Bukkit skin");
        var actual=profile.getTextures().getSkin().toURI();
        require(expected.getHost().equals(actual.getHost())&&expected.getPath().equals(actual.getPath()),
                "native head retains the exact archived Mojang texture hash for its tile");
    }

    private void classicCommit(GameSession room,ClassicGame game,int actor,String action)throws Exception {
        game.apply(actor,action);
        call(room,"event",new Class<?>[]{int.class,JsonElement.class},actor,new JsonPrimitive(action));
        Field revision=GameSession.class.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(room,revision.getLong(room)+1);
    }

    private void classicRay(ClassicTable table,ClassicFixture fixture,Player player,ClassicLayout.Slot slot) {
        var hit=table.hit(player,fixture.origin.clone().add(slot.x(),2,slot.z()),new org.bukkit.util.Vector(0,-1,0));
        require(hit!=null&&hit.id().equals(slot.id()),"rendered slot shares pointer coordinates: "+slot.id()+" actual="+(hit==null?"null":hit.id()));
    }

    @SuppressWarnings("unchecked")
    private void classicClick(ClassicTable table,ClassicFixture fixture,Player player,String action,String kind)throws Exception {
        Object view=((Map<?,?>)field(table,"views")).get(player.getUniqueId());
        ClassicLayout layout=(ClassicLayout)field(view,"layout");List<String> controls=(List<String>)field(view,"controlActions");
        String[] parts=action.split(":");String returned;
        int index=controls.indexOf(action);
        if(action.equals("roll")&&Set.of("backgammon","monopoly").contains(kind)) {
            Location origin=(Location)field(table,"origin");
            double x=GoldenEggOVO.TDGames.Shared.render.TablePlacement.sideTrayX(ClassicLayout.tableHalfWidth(kind));
            var hit=table.hit(player,origin.clone().add(x,2,0),new org.bukkit.util.Vector(0,-1,0));
            require(hit!=null&&hit.id().equals("@roll"),"dice action has a physical side-table target");
            returned=table.action(player,"@roll");
        } else if(index>=0) {
            returned=classicControl(table,fixture,player,index);
        } else if(parts.length>=3&&(parts[0].equals("move")||parts[0].equals("drop"))) {
            int seat=((Number)field(view,"seat")).intValue();String source=parts[0].equals("drop")?"h"+seat+parts[1]:parts[1];String target=parts[2];
            if(kind.equals("klondike")&&target.matches("t[0-6]")&&layout.slot(target)==null) {
                final String prefix=target+"_";
                target=layout.slots().stream().map(ClassicLayout.Slot::id).filter(id->id.startsWith(prefix)).max(Comparator.comparingInt(id->Integer.parseInt(id.substring(3)))).orElseThrow();
            }
            require(layout.slot(source)!=null&&layout.slot(target)!=null,"move source and target have real hit slots: "+action);
            classicRay(table,fixture,player,layout.slot(source));require(table.action(player,source)==null,"source selection does not commit action");
            classicRay(table,fixture,player,layout.slot(target));returned=table.action(player,target);
        } else {
            String target=kind.equals("mastermind")&&parts[0].equals("slot")?"draft"+parts[1]
                    :parts.length==2?parts[1]:kind.equals("klondike")&&(action.equals("draw")||action.equals("recycle"))?"stock":null;
            require(target!=null&&layout.slot(target)!=null,"cell action has a rendered target: "+action);
            classicRay(table,fixture,player,layout.slot(target));returned=table.action(player,target);
        }
        if(returned==null) {
            controls=(List<String>)field(view,"controlActions");
            int choice=controls.indexOf(action);
            if(field(view,"input")!=null)choice=controls.indexOf("@edit:confirm");
            require(choice>=0,"pending action has a physical table control: "+action);
            returned=classicControl(table,fixture,player,choice);
        }
        require(action.equals(returned),"display input returns intended rule action: "+action+" actual="+returned);
    }

    private String classicControl(ClassicTable table,ClassicFixture fixture,Player player,int index)throws Exception {
        Object view=((Map<?,?>)field(table,"views")).get(player.getUniqueId());
        ClassicLayout layout=(ClassicLayout)field(view,"layout");
        List<?> controls=(List<?>)field(view,"controlActions");
        int pages=0;
        while(layout.slot("@control/"+index)==null) {
            require(pages++<=controls.size()/ClassicLayout.CONTROLS_PER_PAGE+1,"bounded control page navigation");
            int current=((Number)field(view,"page")).intValue(),target=index/ClassicLayout.CONTROLS_PER_PAGE;
            table.action(player,target>current?"@page/next":"@page/prev");layout=(ClassicLayout)field(view,"layout");
        }
        classicRay(table,fixture,player,layout.slot("@control/"+index));
        return table.action(player,"@control/"+index);
    }

    @SuppressWarnings("unchecked")
    private int classicSettle(ClassicTable table,ClassicGame game)throws Exception {
        var cells=game.cells();var info=game.publicInfo();
        int frames=0;
        int cardFrames=0;
        while(true) {
            boolean active=table.rolling()||table.moving();
            List<Object> turns=new ArrayList<>();
            if(!active)break;
            require(frames++<160,"bounded classic animation must settle");
            for(Object view:((Map<?,?>)field(table,"views")).values())turns.addAll((List<?>)field(view,"turns"));
            table.tick();
            for(Object turn:turns) {cardPivot(turn);cardFrames++;}
            require(game.cells().equals(cells)&&game.publicInfo().equals(info),"animation cannot change rule state");
        }
        if(cardFrames>0)getLogger().info("BOARDS_MEMORY_PIVOT_PASS frames="+cardFrames+" real_displays=true client_visual_test=false");
        return frames;
    }

    @SuppressWarnings("unchecked")
    private void cardPivot(Object turn)throws Exception {
        int frame=(int)field(turn,"frame");
        Map<org.bukkit.entity.Entity,Location> faces=(Map<org.bukkit.entity.Entity,Location>)field(turn,"faces");
        Map<org.bukkit.entity.Display,org.bukkit.util.Transformation> originals=
                (Map<org.bukkit.entity.Display,org.bukkit.util.Transformation>)field(turn,"originalPoses");
        var body=(org.bukkit.entity.Display)faces.keySet().iterator().next();
        var bodyAt=faces.get(body).toVector().toVector3f();
        var restCenter=displayMatrix(originals.get(body)).transformPosition(new org.joml.Vector3f(.5f)).add(bodyAt);
        var center=displayMatrix(body.getTransformation()).transformPosition(new org.joml.Vector3f(.5f))
                .add(body.getLocation().toVector().toVector3f());
        var normal=body.getTransformation().getLeftRotation().transform(new org.joml.Vector3f(0,1,0));
        for(String side:List.of("backs","faces")) {
            Map<org.bukkit.entity.Entity,Location> parts=(Map<org.bukkit.entity.Entity,Location>)field(turn,side);
            for(var entry:parts.entrySet()) {
                boolean removed=side.equals("backs")?frame>=9:(boolean)field(turn,"collected");
                require(entry.getKey().isValid()!=removed,"memory faces survive until their closing or collection finishes");
                if(frame>9||!entry.getKey().isValid())continue;
                require(entry.getKey().getLocation().distance(entry.getValue())<2e-6,"memory anchors stay fixed during display interpolation");
                var part=(org.bukkit.entity.Display)entry.getKey();
                var local=new org.joml.Vector3f(part instanceof org.bukkit.entity.ItemDisplay?0:.5f);
                var rest=displayMatrix(originals.get(part)).transformPosition(new org.joml.Vector3f(local))
                        .add(entry.getValue().toVector().toVector3f());
                var current=displayMatrix(part.getTransformation()).transformPosition(new org.joml.Vector3f(local))
                        .add(part.getLocation().toVector().toVector3f());
                require(Math.abs(current.distance(center)-rest.distance(restCenter))<3e-5,"memory layers retain their rigid card offsets");
                if(part instanceof org.bukkit.entity.ItemDisplay) {
                    var pictureNormal=part.getTransformation().getLeftRotation().transform(new org.joml.Vector3f(0,0,1));
                    require(Math.abs(Math.abs(pictureNormal.dot(normal))-1)<2e-5,
                            "memory picture rotates with the correct card face");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void classicCheckViews(ClassicTable table,ClassicFixture fixture,GameSession room,ClassicGame game)throws Exception {
        Map<?,?> views=(Map<?,?>)field(table,"views");require(views.size()==3,room.kind+" three real viewer models required");
        for(Player player:fixture.players) {
            Object view=views.get(player.getUniqueId());require(view!=null,"nearby fixture player gets an actual view");
            List<org.bukkit.entity.Entity> privateParts=new ArrayList<>();
            for(Object visual:((Map<?,?>)field(view,"visuals")).values())privateParts.addAll((List<org.bukkit.entity.Entity>)field(visual,"parts"));
            for(Object parts:((Map<?,?>)field(view,"controls")).values())privateParts.addAll((List<org.bukkit.entity.Entity>)parts);
            for(Object parts:((Map<?,?>)field(view,"highlights")).values())privateParts.addAll((List<org.bukkit.entity.Entity>)parts);
            var observedCells=game.cellsFor(room.seat(player.getUniqueId()));
            boolean unmarkedSea=room.kind.equals("battleship")&&room.seat(player.getUniqueId())<0
                    &&observedCells.stream().allMatch(cell->cell.piece().equals("water"));
            if(unmarkedSea) {
                require(privateParts.isEmpty(),"unmarked observer sea must not expose hidden fleet entities");
                require(((Map<?,?>)field(view,"visuals")).size()==observedCells.size(),"all observer sea cells are tracked without extra displays");
            } else require(!privateParts.isEmpty(),room.kind+" viewer has real dynamic entities");
            for(var entity:privateParts) {
                require(entity!=null&&entity.isValid()&&entity.getWorld().equals(fixture.real),room.kind+" actual live server-world display");
                require(!entity.isPersistent()&&!entity.isVisibleByDefault(),room.kind+" private display hidden by default and temporary");
                require(fixture.shown.get(player.getUniqueId()).contains(entity.getUniqueId()),room.kind+" own display shown to intended viewer");
                for(Player other:fixture.players)if(other!=player)require(!fixture.shown.get(other.getUniqueId()).contains(entity.getUniqueId()),room.kind+" private display never shown to another viewer");
                String transform=((org.bukkit.entity.Display)entity).getTransformation().toString();
                require(!transform.contains("NaN")&&!transform.contains("Infinity"),room.kind+" finite display transform");
            }
            for(var cell:observedCells) {
                Object visual=((Map<?,?>)field(view,"visuals")).get(cell.id());
                if(unmarkedSea)require(visual!=null,"observer sea cell retained: "+cell.id());
                if(visual!=null)require(cell.equals(field(visual,"cell")),room.kind+" rendered cell matches filtered seat observation");
            }
        }
        Player observer=fixture.players.get(2);
        require(((List<?>)field(views.get(observer.getUniqueId()),"actions")).isEmpty(),"spectator has no playable rule actions");
        require(((List<?>)field(views.get(observer.getUniqueId()),"controlActions")).isEmpty(),"spectator has no private controls");
        for(String name:List.of("nativeFurniture","packedFurniture"))for(var entity:(List<org.bukkit.entity.Entity>)field(table,name)) {
            require(entity.isValid()&&!entity.isVisibleByDefault()&&!entity.isPersistent(),room.kind+" furniture is real and audience controlled");
            for(Player player:fixture.players) {
                boolean packed=(boolean)field(views.get(player.getUniqueId()),"packed");
                require(fixture.shown.get(player.getUniqueId()).contains(entity.getUniqueId())==name.equals(packed?"packedFurniture":"nativeFurniture"),room.kind+" furniture shown only to matching client mode");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void diceInterpolation(Object tray)throws Exception {
        int nativeParts=0,packedParts=0;
        for(String layer:List.of("nativeDice","packedDice"))for(Object die:(List<?>)field(tray,layer)) {
            for(var part:(List<org.bukkit.entity.Entity>)field(die,"entities")) {
                require(part.isValid(),"dice interpolation probe exercises a live display");
                var display=(org.bukkit.entity.Display)part;
                boolean packed=part instanceof org.bukkit.entity.ItemDisplay;
                require(display.getInterpolationDuration()==(packed?2:0),"native dice commit one rigid pose; packed dice retain interpolation");
                if(packed)packedParts++;else nativeParts++;
            }
        }
        require(nativeParts>0,"dice interpolation probe cannot pass without real native parts");
        getLogger().info("BOARDS_DICE_INTERPOLATION_PASS native_parts="+nativeParts+" packed_parts="+packedParts+" real_displays=true client_visual_test=false");
    }

    @SuppressWarnings("unchecked")
    private void hexGeometry(ClassicTable table,ClassicFixture fixture)throws Exception {
        int nativeStones=0,packedStones=0,edgesChecked=0;
        for(Player player:fixture.players) {
            Object view=((Map<?,?>)field(table,"views")).get(player.getUniqueId());
            ClassicLayout layout=(ClassicLayout)field(view,"layout");
            for(Object visual:((Map<?,?>)field(view,"visuals")).values()) {
                List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(visual,"parts");
                if(parts.isEmpty())continue;
                ClassicLayout.Slot slot=(ClassicLayout.Slot)field(visual,"slot");
                if((boolean)field(view,"packed")) {
                    require(parts.size()==1&&parts.getFirst() instanceof org.bukkit.entity.ItemDisplay,"hex packed stone uses one real item display");
                    var item=(org.bukkit.entity.ItemDisplay)parts.getFirst();
                    require(item.getItemStack().getItemMeta().getItemModel()!=null,"hex packed stone resolves an actual CE item model");
                    packedStones++;
                } else {
                    require(parts.size()==3,"hex native stone uses three real rhombi");
                    for(var entity:parts) {
                        var display=(org.bukkit.entity.BlockDisplay)entity;
                        var at=display.getLocation();
                        var matrix=new org.joml.Matrix4f().translate((float)(at.getX()-fixture.origin.getX()),
                            (float)(at.getY()-fixture.origin.getY()),(float)(at.getZ()-fixture.origin.getZ()))
                            .mul(displayMatrix(display.getTransformation()));
                        for(float y:new float[]{0,1})for(float x:new float[]{0,1})for(float z:new float[]{0,1}) {
                            var p=matrix.transformPosition(new org.joml.Vector3f(x,y,z));
                            require(Math.abs(p.y-slot.height()-.045*y)<2e-5,"hex real SVD preserves stone height");
                            double dx=p.x-slot.x(),dz=p.z-slot.z();boolean vertex=Math.hypot(dx,dz)<2e-5;
                            for(int i=0;i<6;i++) {
                                double angle=i*Math.PI/3;
                                vertex|=Math.hypot(dx-Math.cos(angle)*slot.width()*.42,
                                    dz-Math.sin(angle)*slot.depth()*.42/Math.sin(Math.PI/3))<2e-5;
                            }
                            require(vertex,"hex real SVD preserves exact center or outer vertex");
                        }
                    }
                    nativeStones++;
                }
            }
            for(String id:List.of("0,0","10,0","0,10","10,10","5,5")) {
                table.hover(player,id);var slot=layout.slot(id);
                List<org.bukkit.entity.Entity> edges=((Map<String,List<org.bukkit.entity.Entity>>)field(view,"highlights")).get(id);
                require(edges!=null&&edges.size()==6,"hex real outline has six edges");int[] connections=new int[6];
                for(var entity:edges) {
                    var display=(org.bukkit.entity.BlockDisplay)entity;var at=display.getLocation();
                    var matrix=new org.joml.Matrix4f().translate((float)(at.getX()-fixture.origin.getX()),0,
                        (float)(at.getZ()-fixture.origin.getZ())).rotateY((float)Math.toRadians(-at.getYaw())).mul(displayMatrix(display.getTransformation()));
                    for(float end:new float[]{0,1}) {
                        var p=matrix.transformPosition(new org.joml.Vector3f(end,.5f,.5f));int matched=-1;
                        for(int i=0;i<6;i++) {
                            double angle=i*Math.PI/3;
                            if(Math.hypot(p.x-slot.x()-Math.cos(angle)*slot.width()/2,
                                p.z-slot.z()-Math.sin(angle)*slot.depth()/2/Math.sin(Math.PI/3))<2e-5)matched=i;
                        }
                        require(matched>=0,"hex real outline endpoint meets its tile corner");connections[matched]++;
                    }
                    edgesChecked++;
                }
                for(int count:connections)require(count==2,"hex real outline is closed at every corner");
            }
            table.hover(player,null);
        }
        require(nativeStones>0,"hex geometry probe must exercise real native stones");
        getLogger().info("BOARDS_HEX_GEOMETRY_PASS native_stones="+nativeStones+" packed_stones="+packedStones+" edges="+edgesChecked+" real_svd=true client_visual_test=false");
    }

    private void classicPrivacy(ClassicTable table,ClassicFixture fixture,String kind,ClassicGame game)throws Exception {
        Map<?,?> views=(Map<?,?>)field(table,"views");
        if(kind.equals("battleship"))for(int owner=0;owner<2;owner++) {
            final String prefix="b"+owner+"_";
            List<Cell> ships=game.cellsFor(owner).stream().filter(cell->cell.id().startsWith(prefix)&&cell.piece().startsWith("ship_")).toList();
            require(ships.size()==17,"real hidden fleet contains all seventeen ship cells");
            for(Cell ship:ships)for(int viewer=0;viewer<3;viewer++) {
                Object visual=((Map<?,?>)field(views.get(fixture.players.get(viewer).getUniqueId()),"visuals")).get(ship.id());
                Cell shown=(Cell)field(visual,"cell");require(shown.piece().equals(viewer==owner?ship.piece():"water"),"unshot fleet hidden from opponent and observer");
            }
        }
        if(kind.equals("mastermind"))for(int slot=0;slot<4;slot++)for(int viewer=0;viewer<3;viewer++) {
            Object visual=((Map<?,?>)field(views.get(fixture.players.get(viewer).getUniqueId()),"visuals")).get("secret"+slot);
            Cell shown=(Cell)field(visual,"cell");require(shown.piece().equals(viewer==0?"color_"+slot:"back"),"secret code only rendered to setter");
        }
        if(kind.equals("memory"))require(game.cells().stream().allMatch(cell->cell.piece().equals("back")),"unflipped cards render only backs to all viewers");
    }

    private static void setField(Object target,String name,Object value)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }

    private static Object fixtureDefault(Class<?> type) {
        if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
        if(type==double.class)return 0D;if(type==float.class)return 0F;if(type==short.class)return (short)0;
        if(type==byte.class)return (byte)0;if(type==char.class)return (char)0;return null;
    }

    private void spectralProbe(TDGamesPlugin plugin, World world) throws Exception {
        Location origin = new Location(world, 8, 83, 8);
        world.getChunkAt(origin).load();
        var edge = world.spawn(origin, org.bukkit.entity.BlockDisplay.class);
        var face = world.spawn(origin, org.bukkit.entity.BlockDisplay.class);
        try {
            GoldenEggOVO.TDGames.Shared.render.TableButtonStyle.hover(List.of(edge, face), true);
            require(edge.isGlowing() && face.isGlowing()
                            && org.bukkit.Color.YELLOW.equals(edge.getGlowColorOverride())
                            && org.bukkit.Color.YELLOW.equals(face.getGlowColorOverride()),
                    "all button model layers receive matching spectral metadata");
            GoldenEggOVO.TDGames.Shared.render.TableButtonStyle.hover(List.of(edge, face), false);
            require(!edge.isGlowing() && !face.isGlowing(), "whole button outline clears on exit");
        } finally { edge.remove(); face.remove(); }
        var room = new GameSession(UUID.randomUUID(), "yacht", 2, 17, 0);
        var viewer = player(world, new AtomicInteger(), true);
        var audience = new GoldenEggOVO.TDGames.Core.render.DisplayAudience(plugin, origin, false);
        Class<?> type = Class.forName("GoldenEggOVO.TDGames.Shared.render.dice.YachtDie", true, plugin.getClass().getClassLoader());
        var constructor = type.getDeclaredConstructor(TDGamesPlugin.class, GameSession.class, Location.class,
                org.bukkit.NamespacedKey.class, audience.getClass(), boolean.class, double.class, String.class);
        constructor.setAccessible(true);
        var rest = GoldenEggOVO.TDGames.Shared.render.dice.DiceMotion.class.getDeclaredMethod("rest", double.class, int.class);
        rest.setAccessible(true);
        boolean packedAvailable = Boolean.getBoolean("boards.probe.craftengine");
        for (boolean packed : packedAvailable ? List.of(false, true) : List.of(false)) {
            Object die = constructor.newInstance(plugin, room, origin, new org.bukkit.NamespacedKey("3dgames", "probe"),
                    audience, packed, .18, "die0");
            try {
                Object pose = rest.invoke(null, .18, 4);
                call(die, "pose", new Class<?>[]{pose.getClass()}, pose);
                var source = (org.bukkit.entity.Display)field(die, packed ? "packed" : "body");
                var scale = new org.joml.Vector3f(source.getTransformation().getScale());
                var copy = (org.bukkit.entity.Display)call(die, "highlight", new Class<?>[]{TDGamesPlugin.class, Player.class}, plugin, viewer);
                try {
                    require(copy.isValid() && !copy.isPersistent() && !copy.isVisibleByDefault() && copy.isGlowing()
                            && org.bukkit.Color.YELLOW.equals(copy.getGlowColorOverride()), "private temporary die outline");
                    require(!source.isGlowing() && scale.equals(source.getTransformation().getScale()), "shared die remains unchanged");
                    require(copy instanceof org.bukkit.entity.ItemDisplay == packed, "outline uses the matching native or packed model");
                } finally { copy.remove(); require(!copy.isValid(), "private outline removed"); }
            } finally { call(die, "close", new Class<?>[0]); }
        }
        getLogger().info("BOARDS_SPECTRAL_PASS buttons=true dice_native=true dice_packed=" + packedAvailable
                + " private=true cleanup=true client_visual_test=false");
    }

    /** Resolve native sounds against the real server registry and exercise their dispatch. */
    private void soundProbe(Plugin plugin, World world) throws Exception {
        ClassLoader loader=plugin.getClass().getClassLoader();
        Class<?> sounds=Class.forName("GoldenEggOVO.TDGames.Shared.audio.TableSounds",true,loader);
        Class<?> cueType=Class.forName("GoldenEggOVO.TDGames.Shared.audio.TableSounds$Cue",true,loader);
        Method move=sounds.getDeclaredMethod("move",String.class,int.class,String.class,List.class,List.class);move.setAccessible(true);
        Method play=sounds.getDeclaredMethod("play",plugin.getClass(),Location.class,cueType);play.setAccessible(true);
        Method nativeSound=cueType.getDeclaredMethod("sound");nativeSound.setAccessible(true);
        Location at=new Location(world,8,84,0);int games=0,cues=0;
        for(String kind:List.of("chess","xiangqi","gomoku","checkers","draughts","reversi","go9","go13","go","connectfour","ludo","yacht")){
            BoardGame game=GameFactory.create(kind,2,1);var before=game.cells();String action=game.legalActions(0).getFirst();game.apply(0,action);
            Object cue=move.invoke(null,kind,0,action,before,game.cells());play.invoke(null,plugin,at,cue);games++;
        }
        for(Field field:sounds.getDeclaredFields())if(field.getType()==cueType){
            field.setAccessible(true);Object cue=field.get(null);org.bukkit.Sound sound=(org.bukkit.Sound)nativeSound.invoke(cue);
            require(org.bukkit.Registry.SOUNDS.getKey(sound)!=null,"native sound registered: "+field.getName());
            play.invoke(null,plugin,at,cue);cues++;
        }
        getLogger().info("BOARDS_SOUNDS_PASS games="+games+" cues="+cues+" client_audio_test=false");
    }

    /** Compare an imported synthetic snapshot against the real startup restore path. */
    private void snapshotProbe(Plugin plugin) throws Exception {
        JsonObject source=JsonParser.parseString(Files.readString(Path.of("multi-room-snapshot.json"))).getAsJsonObject();
        Map<?,?> restored=(Map<?,?>)field(field(call(plugin,"games",new Class<?>[0]),"runtime"),"sessions");Set<String> kinds=new HashSet<>();int events=0;
        require(restored.size()==source.getAsJsonArray("rooms").size(),"all snapshot rooms restored");
        for(JsonElement element:source.getAsJsonArray("rooms")){
            JsonObject saved=element.getAsJsonObject();Object room=restored.get(UUID.fromString(saved.get("id").getAsString()));
            require(room!=null,"original room ID restored");String kind=saved.get("kind").getAsString();kinds.add(kind);
            require(!(boolean)field(room,"restoring"),"restored room leaves replay mode");
            require(kind.equals(field(room,"kind"))&&saved.get("capacity").getAsInt()==(int)field(room,"capacity"),"kind and capacity preserved");
            require(saved.get("seed").getAsLong()==(long)field(room,"seed"),"seed preserved");
            require(saved.get("anchorWorld").getAsString().equals(field(room,"anchorWorld").toString()),"world preserved");
            for(String key:List.of("anchorX","anchorY","anchorZ"))require(saved.get(key).getAsDouble()==(double)field(room,key),"position preserved");
            require(new Gson().toJsonTree(field(room,"seats")).equals(saved.get("seats")),"seats preserved");
            JsonArray history=(JsonArray)call(field(room,"history"),"snapshot",new Class<?>[0]),before=saved.getAsJsonArray("history");
            require(history.size()>=before.size(),"saved history retained");
            for(int i=0;i<before.size();i++)require(history.get(i).equals(before.get(i)),"saved event unchanged");
            Map<String,String> options=new LinkedHashMap<>();
            if(saved.has("options"))saved.getAsJsonObject("options").entrySet().forEach(e->options.put(e.getKey(),e.getValue().getAsString()));
            require(field(room,"options").equals(options),"selected rules preserved");
            boolean savedTray = saved.has("sideTray") && saved.get("sideTray").getAsBoolean();
            boolean needsTray = Set.of("backgammon", "monopoly").contains(kind) || savedTray;
            require((boolean)field(room,"sideTray") == needsTray, "saved or required dice stand restored");
            BoardGame live=(BoardGame)field(room,"board"),copy=GameFactory.create(kind,saved.get("capacity").getAsInt(),saved.get("seed").getAsLong(),options);
            for(JsonElement e:history){JsonObject action=e.getAsJsonObject();copy.apply(action.get("seat").getAsInt(),action.get("action").getAsString());}
            require(copy.cells().equals(live.cells())&&copy.currentPlayer()==live.currentPlayer()&&copy.finished()==live.finished()&&Objects.equals(copy.outcome(),live.outcome()),"restored rule state matches history");
            if(copy instanceof HandGame expected&&live instanceof HandGame actual)for(int seat=0;seat<copy.playerCount();seat++)
                require(expected.hand(seat).equals(actual.hand(seat))&&expected.discards(seat).equals(actual.discards(seat))&&expected.exposed(seat).equals(actual.exposed(seat)),"private hands and public tiles replay identically");
            require(Set.of("PLAYING","FINISHED").contains(field(room,"phase").toString()),"restored active or finished phase");events+=before.size();
        }
        Set<String> expected=new HashSet<>();for(JsonElement room:source.getAsJsonArray("rooms"))expected.add(room.getAsJsonObject().get("kind").getAsString());
        require(kinds.equals(expected),"all current snapshot kinds");
        require(((Map<?,?>)field(field(plugin,"tableWorld"),"views")).size()==restored.size(),"one model per restored room");
        getLogger().info("BOARDS_SNAPSHOT_RESTORE_PASS rooms="+restored.size()+" saved_events="+events+" kinds="+kinds.size());
    }

    /** Validate the raise input against the real Paper dialog provider. */
    @SuppressWarnings("unchecked")
    private void pokerRaiseProbe(Plugin plugin, Object menus, World world) throws Exception {
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> roomType = Class.forName("GoldenEggOVO.TDGames.Shared.session.GameSession", true, loader);
        Class<?> factory = Class.forName("GoldenEggOVO.TDGames.Core.game.GameFactory", true, loader);
        var constructor = roomType.getDeclaredConstructor(UUID.class, String.class, int.class, long.class, int.class);
        constructor.setAccessible(true);
        UUID id = UUID.randomUUID();
        Object room = constructor.newInstance(id, "texas-holdem", 2, 1L, 998);
        Object game = factory.getMethod("create", String.class, int.class, long.class).invoke(null, "texas-holdem", 2, 1L);
        int turn = (int) call(game, "currentPlayer", new Class<?>[0]);
        AtomicInteger shown = new AtomicInteger();
        Player actor = player(world, shown, true);
        Player other = player(world, shown, true, UUID.randomUUID());
        for (int seat = 0; seat < 2; seat++) {
            Player seated = seat == turn ? actor : other;
            call(room, "join", new Class<?>[]{UUID.class, String.class}, seated.getUniqueId(), seated.getName());
        }
        var board = roomType.getDeclaredField("board");
        board.setAccessible(true);
        board.set(room, game);
        Map<UUID, Object> rooms = (Map<UUID, Object>) field(field(call(plugin,"games",new Class<?>[0]),"runtime"),"sessions");
        rooms.put(id, room);
        try {
            call(menus, "pokerRaise", new Class<?>[]{Player.class, roomType, long.class}, actor, room, 0L);
            require(shown.get() == 1, "legal Raise opens a native input dialog");
            call(menus, "pokerRaise", new Class<?>[]{Player.class, roomType, long.class}, other, room, 0L);
            require(shown.get() == 1, "non-acting seat cannot open Raise");
            getLogger().info("BOARDS_POKER_RAISE_PASS legal-dialog=opened other-seat=rejected");
        } finally {
            rooms.remove(id);
        }
    }

    /** Build and navigate real Dialog objects while keeping synthetic rooms out of saves. */
    @SuppressWarnings("unchecked")
    private void menuExperienceProbe(Plugin plugin,Object menus,Player player) throws Exception {
        Map<UUID,Object> rooms=(Map<UUID,Object>)field(field(call(plugin,"games",new Class<?>[0]),"runtime"),"sessions");List<UUID> added=new ArrayList<>();
        Class<?> roomType=Class.forName("GoldenEggOVO.TDGames.Shared.session.GameSession",true,plugin.getClass().getClassLoader());
        var constructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);constructor.setAccessible(true);
        try {
            for(Object room:rooms.values())if((int)call(room,"seat",new Class<?>[]{UUID.class},player.getUniqueId())>=0){
                call(menus,"main",new Class<?>[]{Player.class},player);
                require(!clickMenu(menus,player,"id","rooms"),"seated player cannot browse other rooms from the catalog");
                require(!menuLabel(menus,player,"resume").isEmpty(),"seated player can resume");
            }
            player=player(player.getWorld(),new AtomicInteger(),true,UUID.randomUUID());
            for(int i=0;i<25;i++){UUID id=UUID.randomUUID();rooms.put(id,constructor.newInstance(id,"chess",2,1L,i));added.add(id);}
            call(menus,"games",new Class<?>[]{Player.class,String.class},player,"chess");Set<String> seen=new HashSet<>();
            for(int page=0;page<4;page++){
                Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
                List<?> buttons=(List<?>)call(session,"buttons",new Class<?>[0]);
                for(Object button:buttons){String id=(String)call(button,"id",new Class<?>[0]);if(id.startsWith("room-"))seen.add(id);}
                if(!clickMenu(menus,player,"id","next"))break;
            }
            require(seen.size()==25,"every room reachable through native pagination");
            UUID cardRoom=UUID.randomUUID();rooms.put(cardRoom,constructor.newInstance(cardRoom,"color-eight",5,1L,26));added.add(cardRoom);
            call(menus,"main",new Class<?>[]{Player.class},player);
            require(clickMenu(menus,player,"id","rooms"),"catalog opens the shared room browser");
            Set<String> globalSeen=new HashSet<>();
            for(int page=0;page<4;page++){
                Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
                for(Object button:(List<?>)call(session,"buttons",new Class<?>[0])){
                    String id=(String)call(button,"id",new Class<?>[0]);require(!id.equals("entry"),"global browser has no game-specific create button");
                    if(id.startsWith("room-")){globalSeen.add(id);if(id.equals("room-"+cardRoom))require(((String)call(button,"label",new Class<?>[0])).contains("Color Eight")&&((String)call(button,"label",new Class<?>[0])).contains(cardRoom.toString().substring(0,6)),"room name contains game and code");}
                }
                if(!clickMenu(menus,player,"id","next"))break;
            }
            for(UUID id:added)require(globalSeen.contains("room-"+id),"mixed-game room reachable through shared browser");
            require(clickMenu(menus,player,"id","back"),"shared browser returns to catalog");
            for(String category : new String[]{"single-player","multiplayer"}) {
                require(clickMenu(menus,player,"id",category),"catalog opens " + category);
                require(clickMenu(menus,player,"id","back"),"category returns to catalog");
            }
            require(clickMenu(menus,player,"id","multiplayer"),"catalog opens multiplayer dice games");
            require(clickMenu(menus,player,"id","ludo"),"dice category selects game setup");
            Object setupSession=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
            for(Object button:(List<?>)call(setupSession,"buttons",new Class<?>[0]))require(!"rooms".equals(call(button,"id",new Class<?>[0])),"game setup omits duplicate browser");
            require(clickMenu(menus,player,"id","details"),"detailed Ludo rules reachable");
            require(clickMenu(menus,player,"id","rule-blocking"),"Ludo blocking setting cycles");
            require(menuLabel(menus,player,"rule-blocking").contains("Any Pawn Blocks"),"blocking change retained in setup");
            require(clickMenu(menus,player,"id","back"),"settings return preserves draft");
            require(!clickMenu(menus,player,"id","mode"),"setup omits the retired mode selector");
            require(!menuLabel(menus,player,"start").isEmpty(),"setup retains room creation");
            call(menus,"setup",new Class<?>[]{Player.class,String.class},player,"mahjong");
            Set<String> profiles=new HashSet<>();
            for(int i=0;i<4;i++){profiles.add(menuLabel(menus,player,"rule-profile"));require(clickMenu(menus,player,"id","rule-profile"),"regional profile cycles");}
            require(profiles.size()==4,"all four regional profiles reachable");
            getLogger().info("BOARDS_MENU_FLOW_PASS rooms=26 pagination=4 shared-browser=mixed-games setup-browser=absent setup-rules=reachable");
        } finally {added.forEach(rooms::remove);call(menus,"forget",new Class<?>[]{Player.class},player);}
    }
    private String menuLabel(Object menus,Player player,String id) throws Exception {
        Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
        for(Object button:(List<?>)call(session,"buttons",new Class<?>[0]))if(id.equals(call(button,"id",new Class<?>[0])))return (String)call(button,"label",new Class<?>[0]);
        throw new IllegalStateException("Missing menu entry: "+id);
    }
    private boolean clickMenu(Object menus,Player player,String property,String value) throws Exception {
        Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
        List<?> buttons=(List<?>)call(session,"buttons",new Class<?>[0]);
        for(int i=0;i<buttons.size();i++)if(value.equals(call(buttons.get(i),property,new Class<?>[0]))){
            call(menus,"handle",new Class<?>[]{Player.class,String.class},player,"3dgames:"+call(session,"token",new Class<?>[0])+" "+i);return true;
        }
        return false;
    }

    /** Exercise real Display transforms without adding a persisted room or a client. */
    private void modelProbe(Plugin plugin, World world) throws Exception {
        // Match TableWorldController.retainTableChunks: keep the model's chunks active before spawning Displays.
        for(int z=-1;z<=0;z++)world.getChunkAt(0,z).addPluginChunkTicket(this);
        ClassLoader loader=plugin.getClass().getClassLoader();
        Class<?> roomType=Class.forName("GoldenEggOVO.TDGames.Shared.session.GameSession",true,loader);
        Class<?> factory=Class.forName("GoldenEggOVO.TDGames.Core.game.GameFactory",true,loader);
        Class<?> gameType=Class.forName("GoldenEggOVO.TDGames.Shared.game.BoardGame",true,loader);
        Class<?> viewType=Class.forName("GoldenEggOVO.TDGames.Shared.render.TableView",true,loader);
        var roomConstructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);
        roomConstructor.setAccessible(true);
        for(String kind:List.of("connectfour","chess","xiangqi","gomoku","go","go9","go13","reversi","draughts","checkers","ludo")) {
            boolean go=List.of("go","go9","go13").contains(kind);
            Object room=roomConstructor.newInstance(UUID.randomUUID(),kind,2,1L,0);
            Object game=factory.getMethod("create",String.class,int.class,long.class).invoke(null,kind,2,1L);
            Field board=roomType.getDeclaredField("board");board.setAccessible(true);board.set(room,game);
            Player viewer=player(world,new AtomicInteger(),true);
            call(room,"join",new Class<?>[]{UUID.class,String.class},viewer.getUniqueId(),"ModelProbe");
            Field phase=roomType.getDeclaredField("phase");phase.setAccessible(true);
            @SuppressWarnings({"rawtypes","unchecked"}) Object playing=Enum.valueOf((Class)phase.getType(),"PLAYING");phase.set(room,playing);
            if(go)for(String action:List.of("place:0,0","place:8,8","place:1,0","pass","pass"))
                gameType.getMethod("apply",int.class,String.class).invoke(game,gameType.getMethod("currentPlayer").invoke(game),action);
            var constructor=viewType.getDeclaredConstructors()[0];constructor.setAccessible(true);
            Object view=constructor.newInstance(plugin,room,new Location(world,8,83,0),
                new org.bukkit.NamespacedKey("3dgames","probe-model"),field(field(plugin,"tableWorld"),"maps"));
            try {
                Location origin=(Location)field(view,"origin");
                if(!kind.equals("connectfour")) {
                    int frames=0;
                    for(Object item:(List<?>)field(view,"furniture"))if(item instanceof org.bukkit.entity.ItemFrame frame) {
                        require(frame.getFacing()==org.bukkit.block.BlockFace.UP&&!frame.isVisible(),"native map frame faces up invisibly");
                        require(Math.abs(frame.getLocation().getY()-origin.getY())<1e-8,"ItemFrame entity stays at the logical origin");
                        frames++;
                    }
                    require(frames==4,"four live native map frames: "+kind);
                    if(kind.equals("xiangqi"))for(Object item:(List<?>)field(view,"furniture"))
                        if(item instanceof TextDisplay river&&plain(river.text()).equals(GoldenEggOVO.TDGames.Core.text.GameSymbols.XIANGQI_RIVER)) {
                            var at=river.getLocation();var pose=river.getTransformation();
                            var matrix=new org.joml.Matrix4f().rotateY((float)Math.toRadians(-at.getYaw()))
                                .rotateX((float)Math.toRadians(at.getPitch())).mul(displayMatrix(pose));
                            double plane=at.getY()-origin.getY()+matrix.transformPosition(new org.joml.Vector3f()).y();
                            require(Math.abs(plane-(-.023359375)-.0015)<2e-6,"Xiangqi river glyph plane clears rendered map");
                        }
                }
                if(kind.equals("connectfour")){
                    call(view,"cursor",new Class<?>[]{Player.class,BoardSelection.class,String.class},viewer,null,"3,5");
                    Object overlay=((Map<?,?>)field(view,"overlays")).get(viewer.getUniqueId());
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.BlockDisplay> preview=(List<org.bukkit.entity.BlockDisplay>)field(overlay,"hover");
                    require(preview.size()==4,"four landing markers");
                    for(var marker:preview)require(marker.isValid()&&!marker.isVisibleByDefault(),"private live landing marker");
                    call(view,"clear",new Class<?>[]{Player.class},viewer);
                    for(var marker:preview)require(!marker.isValid(),"landing markers removed");
                }
                Map<?,?> previous=new HashMap<>((Map<?,?>)field(view,"tokens"));
                @SuppressWarnings("unchecked") List<String> legal=(List<String>)gameType.getMethod("legalActions",int.class).invoke(game,0);
                String chosen=kind.equals("connectfour")?"drop:3":go?"dead:0,0":legal.getFirst();
                gameType.getMethod("apply",int.class,String.class).invoke(game,0,chosen);
                call(room,"event",new Class<?>[]{int.class,JsonElement.class},0,new JsonPrimitive(chosen));
                Field revision=roomType.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(room,revision.getLong(room)+1);
                call(view,"sync",new Class<?>[0]);
                Map<?,?> current=(Map<?,?>)field(view,"tokens");
                require(!current.isEmpty(),"model pieces rendered after the action");
                if(kind.equals("connectfour"))require(current.size()==1,"one dropped chip rendered");
                if(kind.equals("reversi")||go)for(var entry:previous.entrySet())
                    require(current.get(entry.getKey())==entry.getValue(),kind+" reuses existing tokens");
                if(kind.equals("ludo"))require((boolean)call(view,"rolling",new Class<?>[0]),"dice rolling after recorded roll");
                for(int i=0;i<24;i++)call(view,"tick",new Class<?>[0]);
                require(!(boolean)call(view,"rolling",new Class<?>[0]),"dice settled after bounded animation");
                for(Object token:current.values()) {
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(token,"parts");
                    require(parts.size()>=3,"multi-part piece");
                    Location target=(Location)field(token,"to");
                    for(var part:parts) {
                        require(part.isValid() && !part.isPersistent(),"live non-persistent model part: "+kind+" valid="+part.isValid()+" persistent="+part.isPersistent()+" dead="+part.isDead());
                        Location expected=target.clone();
                        if(!kind.equals("connectfour")&&!(part instanceof org.bukkit.entity.ItemDisplay))expected.add(0,-.024909375,0);
                        if(kind.equals("xiangqi")&&part instanceof TextDisplay)expected.add(0,((Number)field(field(view,"geometry"),"spacing")).doubleValue()*.235,0);
                        require(part.getLocation().distanceSquared(expected)<1e-8,"animation settled");
                        String transform=((org.bukkit.entity.Display)part).getTransformation().toString();
                        require(!transform.contains("NaN")&&!transform.contains("Infinity"),"finite server transformation");
                    }
                    if(!kind.equals("connectfour")) {
                        double lowest=Double.POSITIVE_INFINITY;
                        for(var part:parts)if(part instanceof org.bukkit.entity.BlockDisplay block)
                            lowest=Math.min(lowest,modelBottom(block,origin));
                        double stack=((Number)field(field(token,"token"),"stack")).doubleValue();
                        double spacing=((Number)field(field(view,"geometry"),"spacing")).doubleValue();
                        require(Math.abs(lowest-(-.023359375)-(.00145+stack*spacing))<2e-6,
                            "actual native OBB must clear the rendered map: "+kind+" bottom="+lowest);
                    }
                }
                if(go){
                    Object stone=current.get("0,0");
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(stone,"parts");
                    require(parts.size()==6,"Go stone plus three non-overlapping dead marks");
                    List<org.bukkit.entity.Entity> marks=List.copyOf(parts.subList(3,5));
                    gameType.getMethod("apply",int.class,String.class).invoke(game,0,"dead:0,0");revision.setLong(room,revision.getLong(room)+1);call(view,"sync",new Class<?>[0]);
                    require(current.get("0,0")==stone&&parts.size()==3,"Go unmark keeps stone");
                    for(var mark:marks)require(!mark.isValid(),"Go dead mark removed");
                }
                if(kind.equals("chess"))for(String id:List.of("b1","b8")){
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(current.get(id),"parts");
                    getLogger().info("BOARDS_KNIGHT_HEADING "+id+" yaw="+parts.getFirst().getLocation().getYaw());
                    for(var part:parts)require(Math.abs(Math.IEEEremainder(part.getLocation().getYaw()-(id.equals("b1")?180:0),360))<.001,"knight faces opponent: "+id+" yaw="+part.getLocation().getYaw());
                }
                if(kind.equals("ludo")){
                    call(view,"cursor",new Class<?>[]{Player.class,BoardSelection.class,String.class},viewer,null,"sk0");
                    Object overlay=((Map<?,?>)field(view,"overlays")).get(viewer.getUniqueId());
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.BlockDisplay> hints=(List<org.bukkit.entity.BlockDisplay>)field(overlay,"hover");
                    require(hints.size()==4,"Ludo previews the pawn destination");
                    for(var hint:hints) {
                        require(hint.isValid()&&!hint.isVisibleByDefault(),"private live Ludo preview");
                        require(Math.abs(modelBottom(hint,origin)-(-.023359375)-.00245)<2e-6,"Ludo preview clears rendered map");
                    }
                    call(view,"clear",new Class<?>[]{Player.class},viewer);for(var hint:hints)require(!hint.isValid(),"Ludo preview removed");
                    for(var entry:previous.entrySet())require(current.get(entry.getKey())==entry.getValue(),"Ludo roll retains pawn entities");
                }
                if(!kind.equals("connectfour")&&!kind.equals("ludo")) {
                    String hovered=String.valueOf(current.keySet().iterator().next());
                    call(view,"cursor",new Class<?>[]{Player.class,BoardSelection.class,String.class},viewer,null,hovered);
                    Object overlay=((Map<?,?>)field(view,"overlays")).get(viewer.getUniqueId());
                    List<?> hints=(List<?>)field(overlay,"hover");require(hints.size()==4,"four map hover edges");
                    for(Object hint:hints)require(Math.abs(modelBottom((org.bukkit.entity.BlockDisplay)hint,origin)-(-.023359375)-.00245)<2e-6,
                        "actual hover OBB clears rendered map: "+kind);
                    call(view,"clear",new Class<?>[]{Player.class},viewer);
                }
                if(!kind.equals("connectfour"))getLogger().info("BOARDS_MAP_SURFACE_GAME_PASS kind="+kind+" frame_offset=0 native_piece_gap=0.00145 native_cursor_gap=0.00245 world_obb=true");
            } finally {call(view,"close",new Class<?>[0]);}
        }
        world.removePluginChunkTickets(this);
        getLogger().info("BOARDS_MODELS_PASS connectfour=drop-and-private-preview reversi=reuse-and-flip go=dead-marker-reuse chess=knight-heading ludo=pawns-and-private-preview");
        getLogger().info("BOARDS_MAP_SURFACE_PASS games=10 client_map_y=-0.023359375 frame_offset=0 native_piece_gap=0.00145 native_cursor_gap=0.00245 world_obb=true client_visual_test=false");
    }

    private static double modelBottom(org.bukkit.entity.BlockDisplay display,Location origin) {
        var matrix=displayMatrix(display.getTransformation());double lowest=Double.POSITIVE_INFINITY;
        for(int corner=0;corner<8;corner++) {
            var point=matrix.transformPosition(new org.joml.Vector3f(corner&1,(corner>>1)&1,(corner>>2)&1));
            lowest=Math.min(lowest,display.getLocation().getY()-origin.getY()+point.y());
        }
        return lowest;
    }

    private static org.joml.Matrix4f displayMatrix(org.bukkit.util.Transformation pose) {
        return new org.joml.Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                .scale(pose.getScale()).rotate(pose.getRightRotation());
    }

    private Player player(World world, AtomicInteger dialogs, boolean allowed) {
        UUID id = allowed ? UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420") : UUID.randomUUID();
        return player(world,dialogs,allowed,id);
    }
    private Player player(World world,AtomicInteger dialogs,boolean allowed,UUID id) {
        return player(world, dialogs, allowed, id, new Location(world, 0.5, 83, 0.5));
    }
    private Player player(World world,AtomicInteger dialogs,boolean allowed,UUID id,Location location) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "BoardsProbe";
                case "getWorld" -> world;
                case "getEyeHeight" -> 1.62d;
                case "getLocation", "getEyeLocation" -> location.clone();
                case "getPersistentDataContainer" -> world.getPersistentDataContainer();
                case "isInsideVehicle", "isFlying", "isSneaking" -> false;
                case "getPose" -> org.bukkit.entity.Pose.STANDING;
                case "getServer" -> getServer();
                case "isOnline", "isValid", "isPermissionSet" -> true;
                case "isDead", "isOp" -> false;
                case "hasPermission" -> allowed;
                case "getEffectivePermissions" -> java.util.Set.of();
                case "teleport" -> true;
                case "showDialog" -> { require(args[0] != null, "native dialog built"); dialogs.incrementAndGet(); yield null; }
                case "sendMessage", "sendActionBar" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "BoardsProbe";
                default -> null;
            });
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pictureMenuProbe(Plugin plugin, World world) throws Exception {
        Object pack = call(plugin, "pack", new Class<?>[0]);
        Field mode = pack.getClass().getDeclaredField("mode"); mode.setAccessible(true);
        Field available = pack.getClass().getDeclaredField("available"); available.setAccessible(true);
        Object oldMode = mode.get(pack), oldAvailable = available.get(pack);
        AtomicInteger shown = new AtomicInteger();
        Player viewer = player(world, shown, true, new UUID(0, 998));
        var preference = (org.bukkit.NamespacedKey) field(pack, "preference");
        Byte oldPreference = viewer.getPersistentDataContainer().get(preference, org.bukkit.persistence.PersistentDataType.BYTE);
        Map<UUID,Object> requests = (Map<UUID,Object>) field(pack, "requests");
        Object oldRequest = requests.get(viewer.getUniqueId());
        try {
            mode.set(pack, Enum.valueOf((Class) mode.getType(), "MIXED"));
            available.set(pack, (java.util.function.BooleanSupplier) () -> true);
            viewer.getPersistentDataContainer().set(preference, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            fixturePackReady((TDGamesPlugin) plugin, viewer);
            require((boolean)call(pack,"packed",new Class<?>[]{Player.class},viewer),"picture menu fixture is packed");
            Object menus = call(plugin, "menus", new Class<?>[0]);
            call(menus, "main", new Class<?>[]{Player.class}, viewer);
            call(menus, "settings", new Class<?>[]{Player.class}, viewer);
            call(menus, "games", new Class<?>[]{Player.class}, viewer);
            call(menus, "singlePlayer", new Class<?>[]{Player.class}, viewer);
            call(menus, "setup", new Class<?>[]{Player.class, String.class}, viewer, "xiangqi");
            call(menus, "setup", new Class<?>[]{Player.class, String.class}, viewer, "mahjong");
            var submit = new GoldenEggOVO.TDGames.Core.menu.MenuView.Button(Component.text("Save"), null, 300, "save");
            var back = new GoldenEggOVO.TDGames.Core.menu.MenuView.Button(Component.text("Back"), null, 300, "back");
            var form = new GoldenEggOVO.TDGames.Core.menu.MenuView(Component.text("Amount"), List.of(),
                    List.of(new GoldenEggOVO.TDGames.Core.menu.MenuView.Input("amount", Component.text("Amount"), "0", 16)),
                    List.of(submit), back, 1);
            GoldenEggOVO.TDGames.Core.menu.DialogMenus.open(plugin, viewer, form, (action, values) -> {});
            var confirmation = new GoldenEggOVO.TDGames.Core.menu.MenuView(Component.text("Confirm"), List.of(), List.of(),
                    List.of(new GoldenEggOVO.TDGames.Core.menu.MenuView.Button(Component.text("Confirm"), null, 300, "confirm")), back, 1);
            GoldenEggOVO.TDGames.Core.menu.DialogMenus.open(plugin, viewer, confirmation, (action, values) -> {});
            require(shown.get()==8,"picture menus and native input construct real Paper dialogs");
            getLogger().info("PICTURE_MENU_PASS real_paper_builder=true synthetic_ready=true pages=8 input_form=true client_visual_test=false");
        } finally {
            mode.set(pack,oldMode); available.set(pack,oldAvailable);
            if(oldPreference==null)viewer.getPersistentDataContainer().remove(preference);
            else viewer.getPersistentDataContainer().set(preference,org.bukkit.persistence.PersistentDataType.BYTE,oldPreference);
            if(oldRequest==null)requests.remove(viewer.getUniqueId());else requests.put(viewer.getUniqueId(),oldRequest);
            call(pack,"preferred",new Class<?>[]{Player.class},viewer);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
