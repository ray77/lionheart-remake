/*
 * Copyright (C) 2013-2026 Byron 3D Games Studio (www.b3dgs.com) Pierre-Alexandre (contact@b3dgs.com)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.b3dgs.lionheart;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.b3dgs.lionengine.Logger;
import com.b3dgs.lionengine.LoggerFactory;

import com.b3dgs.lionengine.Align;
import com.b3dgs.lionengine.LionEngineException;
import com.b3dgs.lionengine.Media;
import com.b3dgs.lionengine.Medias;
import com.b3dgs.lionengine.Origin;
import com.b3dgs.lionengine.SplitType;
import com.b3dgs.lionengine.Tick;
import com.b3dgs.lionengine.UtilConversion;
import com.b3dgs.lionengine.UtilMath;
import com.b3dgs.lionengine.audio.Audio;
import com.b3dgs.lionengine.audio.AudioFactory;
import com.b3dgs.lionengine.game.Action;
import com.b3dgs.lionengine.game.Configurer;
import com.b3dgs.lionengine.game.FeatureProvider;
import com.b3dgs.lionengine.game.Force;
import com.b3dgs.lionengine.game.feature.Camera;
import com.b3dgs.lionengine.game.feature.CameraTracker;
import com.b3dgs.lionengine.game.feature.Featurable;
import com.b3dgs.lionengine.game.feature.Identifiable;
import com.b3dgs.lionengine.game.feature.Layerable;
import com.b3dgs.lionengine.game.feature.LayerableModel;
import com.b3dgs.lionengine.game.feature.Services;
import com.b3dgs.lionengine.game.feature.Spawner;
import com.b3dgs.lionengine.game.feature.Transformable;
import com.b3dgs.lionengine.game.feature.collidable.Collidable;
import com.b3dgs.lionengine.game.feature.networkable.ComponentNetwork;
import com.b3dgs.lionengine.game.feature.networkable.IdentifiableCreate;
import com.b3dgs.lionengine.game.feature.networkable.Networkable;
import com.b3dgs.lionengine.game.feature.networkable.NetworkedDevice;
import com.b3dgs.lionengine.game.feature.rasterable.Rasterable;
import com.b3dgs.lionengine.game.feature.state.StateHandler;
import com.b3dgs.lionengine.game.feature.tile.map.MapTile;
import com.b3dgs.lionengine.game.feature.tile.map.MapTileGame;
import com.b3dgs.lionengine.game.feature.tile.map.MapTileGroup;
import com.b3dgs.lionengine.game.feature.tile.map.MapTileGroupModel;
import com.b3dgs.lionengine.game.feature.tile.map.TileSetListener;
import com.b3dgs.lionengine.game.feature.tile.map.TileSheetsConfig;
import com.b3dgs.lionengine.game.feature.tile.map.collision.MapTileCollisionModel;
import com.b3dgs.lionengine.game.feature.tile.map.collision.MapTileCollisionRenderer;
import com.b3dgs.lionengine.game.feature.tile.map.collision.MapTileCollisionRendererModel;
import com.b3dgs.lionengine.game.feature.tile.map.persister.MapTilePersister;
import com.b3dgs.lionengine.game.feature.tile.map.persister.MapTilePersisterListener;
import com.b3dgs.lionengine.game.feature.tile.map.raster.MapTileRastered;
import com.b3dgs.lionengine.game.feature.tile.map.raster.MapTileRasteredModel;
import com.b3dgs.lionengine.game.feature.tile.map.viewer.MapTileViewer;
import com.b3dgs.lionengine.game.feature.tile.map.viewer.MapTileViewerModel;
import com.b3dgs.lionengine.geom.Coord;
import com.b3dgs.lionengine.graphic.ColorRgba;
import com.b3dgs.lionengine.graphic.Graphic;
import com.b3dgs.lionengine.graphic.Graphics;
import com.b3dgs.lionengine.graphic.Text;
import com.b3dgs.lionengine.graphic.drawable.Drawable;
import com.b3dgs.lionengine.graphic.drawable.Sprite;
import com.b3dgs.lionengine.graphic.engine.Zooming;
import com.b3dgs.lionengine.helper.DeviceControllerConfig;
import com.b3dgs.lionengine.helper.EntityInputController;
import com.b3dgs.lionengine.helper.MapTileHelper;
import com.b3dgs.lionengine.helper.WorldHelper;
import com.b3dgs.lionengine.io.DeviceController;
import com.b3dgs.lionengine.io.FileReading;
import com.b3dgs.lionengine.io.FileWriting;
import com.b3dgs.lionengine.network.Channel;
import com.b3dgs.lionengine.network.ChannelBuffer;
import com.b3dgs.lionengine.network.Network;
import com.b3dgs.lionengine.network.NetworkType;
import com.b3dgs.lionengine.network.UtilNetwork;
import com.b3dgs.lionengine.network.client.Client;
import com.b3dgs.lionengine.network.client.ClientListener;
import com.b3dgs.lionengine.network.client.ClientUdp;
import com.b3dgs.lionengine.network.server.Server;
import com.b3dgs.lionengine.network.server.ServerListener;
import com.b3dgs.lionengine.network.server.ServerUdp;
import com.b3dgs.lionheart.constant.CollisionName;
import com.b3dgs.lionheart.constant.Extension;
import com.b3dgs.lionheart.constant.Folder;
import com.b3dgs.lionheart.landscape.FactoryLandscape;
import com.b3dgs.lionheart.landscape.ForegroundType;
import com.b3dgs.lionheart.landscape.Landscape;
import com.b3dgs.lionheart.menu.MenuGame;
import com.b3dgs.lionheart.object.EntityModel;
import com.b3dgs.lionheart.object.Snapshotable;
import com.b3dgs.lionheart.object.feature.BulletBounceOnGround;
import com.b3dgs.lionheart.object.feature.Stats;
import com.b3dgs.lionheart.object.feature.StatsDeadListener;
import com.b3dgs.lionheart.object.feature.Trackable;
import com.b3dgs.lionheart.object.feature.Underwater;

/**
 * World game representation.
 */
// CHECKSTYLE IGNORE LINE: FanOutComplexity|DataAbstractionCoupling
final class World extends WorldHelper implements MusicPlayer, LoadNextStage
{
    private static final int VERSUS_WIN_DELAY_MS = 5500;
    private static final int SPEEDRUN_WIN_DELAY_MS = 5500;
    private static final int BATTLE_WIN_DELAY_MS = 5500;
    private static final int ALLDEAD_DELAY_MS = 2000;
    private static final int PARALLEL_LOAD_TIMEOUT_SEC = 30;
    private static final String MAP_BOTTOM = "_bottom";
    private static final double ZOOM_OUT_MAX = 1.35;
    private static final double ZOOM_IN_MAX = 0.9;
    private static final double ZOOM_SPEED = 0.005;
    /** Logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(World.class);

    /**
     * Load player raster.
     * 
     * @param stage The stage configuration.
     * @param featurable The hero reference.
     */
    private static void loadRasterHero(StageConfig stage, Featurable featurable)
    {
        featurable.getFeature(Underwater.class)
                  .loadRaster("raster/" + Folder.HERO + "/valdyn/",
                              ForegroundType.LAVA == stage.getForeground().getType());
    }

    private final MapTileWater mapWater = services.create(MapTileWater.class);
    private final Hud hud = services.create(Hud.class);
    private final Tick tick = new Tick();

    /* Runs inline: a browser build has no worker thread to drain a blocking queue,
     * and the tasks are short. Desktop keeps the same ordering, just without the hop. */
    private final java.util.Deque<Runnable> musicToPlay = new java.util.ArrayDeque<>();
    private final List<Featurable> players = new ArrayList<>();
    private final Map<Integer, String> clients = services.add(new ConcurrentHashMap<>());
    private final Sprite splitNone = Drawable.loadSprite(Medias.create(Folder.SPRITE, "split_none.png"));
    private final Text text;
    private final boolean debug;
    private final GameConfig game;
    private final Tick spawnTick = new Tick();
    private final Zooming zooming;
    private final double zoomPref;
    private final Force zoom;

    private final Camera[] splitCamera;
    private final CameraTracker[] splitTracker;
    private final DeviceController[] splitDevice;
    private final Hud[] splitHud;
    private final int[] splitTrackerInitY;
    private final double[] splitTrackerY;
    private final Landscape[] splitLandscape;

    private CheckpointHandler checkpoints;
    private Cheats cheats;
    private Audio music;
    private DeviceController device;
    private boolean server;
    private boolean client;
    private boolean reload;

    private Landscape landscape;
    private int trackerInitY;
    private double trackerY;
    private StateHandler player;
    private Difficulty difficulty;
    private Action rasterRenderer = () ->
    {
        // Nothing to do
    };

    /**
     * Create the world.
     * 
     * @param services The services reference (must not be <code>null</code>).
     * @param game The game reference (must not be <code>null</code>).
     * @throws LionEngineException If invalid argument.
     */
    World(Services services, GameConfig game)
    {
        super(services);

        final int size = Math.max(9, 9 * (int) Math.floor(source.getHeight() / (double) Constant.RESOLUTION.height()));
        text = Graphics.createText(size);
        text.setColor(ColorRgba.WHITE);
        componentCollision.setVisible(Constant.DEBUG_COLLISIONS);

        this.game = game;
        zoomPref = Settings.getInstance().getZoom();
        zoom = new Force(zoomPref, 0.0, ZOOM_SPEED, 0.01);
        debug = Settings.getInstance().isFlagDebug();

        services.add(tracker);
        services.add(new MusicPlayer()
        {
            @Override
            public void playMusic(Media media)
            {
                World.this.playMusic(media);
            }

            @Override
            public void stopMusic()
            {
                World.this.stopMusic();
            }
        });
        zooming = services.get(Zooming.class);

        map.addFeature(new LayerableModel(4, 2));
        map.addFeature(new MapTilePersisterOptimized(), true);

        camera.setIntervals(Constant.CAMERA_HORIZONTAL_MARGIN, 0);


        final int n = game.getSplit() != SplitType.NONE ? game.getPlayers() - 1 : 0;
        if (n == 2)
        {
            splitNone.load();
            splitNone.prepare();
            splitNone.setOrigin(Origin.MIDDLE);
        }
        splitCamera = new Camera[n];
        splitTracker = new CameraTracker[n];
        splitDevice = new DeviceController[game.getPlayers() > 1 ? game.getPlayers() : 0];
        splitHud = new Hud[n];
        splitTrackerInitY = new int[n];
        splitTrackerY = new double[n];
        splitLandscape = new Landscape[n];

        for (int i = 0; i < n; i++)
        {
            splitCamera[i] = new Camera();
            splitCamera[i].setView(0, 0, source.getWidth(), source.getHeight(), source.getHeight());
            splitHud[i] = new Hud(source, splitCamera[i]);
            splitTrackerInitY[i] = 0;
            splitTrackerY[i] = 0.0;
        }

        if (game.getType().is(GameType.BATTLE))
        {
            spawnTick.addAction(() ->
            {
                for (int i = 0; i < players.size(); i++)
                {
                    players.get(i).getFeature(Stats.class).win();
                }
                playMusic(Music.BOSS_WIN);
                spawnTick.addAction(() -> sequencer.end(MenuGame.class, game.with((InitConfig) null)),
                                    source.getRate(),
                                    BATTLE_WIN_DELAY_MS);
            }, source.getRate(), 176_700);
        }
    }

    /**
     * Drain the pending music tasks, running them in place.
     */
    private void playNextMusicTask()
    {
        while (!musicToPlay.isEmpty())
        {
            musicToPlay.poll().run();
        }
    }

    /**
     * Prepare world.
     */
    void prepare()
    {
        cheats = new Cheats(services, tick);
        checkpoints = services.create(CheckpointHandler.class);
        device = services.get(DeviceController.class);
    }

    /**
     * Prepare network.
     * 
     * @param network The network used.
     * @param closer The closer action.
     * @param init The init config.
     * @throws IOException If network error.
     */
    void prepareNetwork(Network network, AtomicReference<Action> closer, InitConfig init) throws IOException
    {
        if (preparer != null)
        {
            preparer.prepare(this, network, closer, init);
        }
    }

    /**
     * Prepares the multiplayer session.
     *
     * <p>
     * Kept behind a hook so an ahead-of-time compiled browser build never reaches the UDP stack: its static analysis
     * follows branches that never run, and {@code java.net} has no browser equivalent. The desktop entry point
     * installs the real implementation.
     * </p>
     */
    public interface NetworkPreparer
    {
        /**
         * Prepare the session.
         *
         * @param world The world to set up.
         * @param network The network configuration.
         * @param closer Receives the shutdown action.
         * @param init The initial configuration.
         * @throws IOException If unable to start.
         */
        void prepare(World world, Network network, AtomicReference<Action> closer, InitConfig init) throws IOException;
    }

    /** Installed preparer, <code>null</code> for single player only. */
    private static NetworkPreparer preparer;

    /**
     * Install the multiplayer preparer.
     *
     * @param preparer The preparer, <code>null</code> to disable multiplayer.
     */
    public static synchronized void setNetworkPreparer(NetworkPreparer preparer)
    {
        World.preparer = preparer;
    }

    /**
     * Load stage from configuration.
     * 
     * @param settings The settings reference.
     * @param init The initial configuration.
     */
    private void loadStage(Settings settings, InitConfig init)
    {
        final StageConfig stage = services.add(StageConfig.imports(new Configurer(init.getStage())));

        if (RasterType.DIRECT == settings.getRaster())
        {
            loadRasterDirect(stage);
        }

        if (settings.isRasterCheck())
        {
            Util.run(stage.getBackground());
        }

        loadMap(settings, stage);

        final FactoryLandscape factoryLandscape = new FactoryLandscape(services,
                                                                       source,
                                                                       settings.isFlickerBackground(),
                                                                       settings.isFlickerForeground());
        landscape = services.add(factoryLandscape.createLandscape(stage.getBackground(), stage.getForeground()));

        for (int i = 0; i < splitLandscape.length; i++)
        {
            splitLandscape[i] = factoryLandscape.createLandscape(stage.getBackground(), stage.getForeground());
        }

        final Optional<Coord> spawn = init.getSpawn();
        checkpoints.load(stage, spawn);

        players.clear();
        if (!server && !client)
        {
            services.add(createPlayer(settings, init, stage).getFeature(Trackable.class));
        }

        for (int i = 0; i < splitCamera.length; i++)
        {
            splitTracker[i] = new CameraTracker(splitCamera[i]);
            camera.setInternal(i + 1, splitCamera[i]);

            final Featurable player = createPlayerSplit(settings, init, stage, i + 1);
            player.getFeature(EntityModel.class).setCamera(splitCamera[i]);
            player.getFeature(EntityModel.class).setTracker(splitTracker[i]);
            splitHud[i].setFeaturable(player);

            splitTracker[i].addFeature(new LayerableModel(player.getFeature(Layerable.class)
                                                                .getLayerRefresh()
                                                                .intValue()
                                                          + 1));
            splitTrackerInitY[i] = player.getFeature(Transformable.class).getHeight() / 2 + 8;
            splitTracker[i].setOffset(0, splitTrackerInitY[i]);
            splitTracker[i].track(player);

            handler.add(splitTracker[i]);
        }

        for (int i = 0; i < splitDevice.length; i++)
        {
            splitDevice[i] = DeviceControllerConfig.create(services,
                                                           Medias.create(Constant.INPUT_FILE_DEFAULT),
                                                           game.getControl(i));
            players.get(i).getFeature(EntityModel.class).setInput(splitDevice[i]);
        }

        final WorldType world = stage.getBackground().getWorld();
        // CHECKSTYLE IGNORE LINE: AnonInnerLength
        checkpoints.addListener(new CheckpointListener()
        {
            @Override
            public void notifyReachCheckpoint(Transformable player, Checkpoint checkpoint, int index)
            {
                if (index > 0 && game.getType().is(GameType.SPEEDRUN))
                {
                    for (int i = 0; i < players.size(); i++)
                    {
                        players.get(i).getFeature(EntityModel.class).removeControl();
                        checkpoints.unregister(players.get(i).getFeature(Transformable.class));
                    }
                    player.getFeature(Stats.class).win();

                    hud.timeStop();
                    for (int i = 0; i < splitHud.length; i++)
                    {
                        splitHud[i].timeStop();
                    }

                    playMusic(Music.BOSS_WIN);
                    spawnTick.addAction(() -> sequencer.end(MenuGame.class, game.with((InitConfig) null)),
                                        source.getRate(),
                                        SPEEDRUN_WIN_DELAY_MS);
                }
            }

            @Override
            public void notifyReachStage(String next, Optional<Coord> spawn)
            {
                loadNextStage(next, 0, spawn);
            }

            @Override
            public void notifyReachBoss(double x, double y)
            {
                if (WorldType.SWAMP == world)
                {
                    camera.setLimitLeft((int) camera.getX());
                    trackerY = 1.0;
                }
                else if (WorldType.LAVA == world)
                {
                    rasterbar.clearRasterbarColor();
                }
                spawn(Medias.create(Folder.BOSS, world.getFolder(), "Boss.xml"), x, y).getFeature(EntityModel.class)
                                                                                      .setNext(stage.getBossNext(),
                                                                                               Optional.empty());
                playMusic(Music.BOSS);
            }
        });

        if (RasterType.CACHE == settings.getRaster())
        {
            stage.getRasterFolder().ifPresent(r ->
            {
                final Media rasterMedia = Medias.create(r, Constant.RASTER_FILE_TILE);
                if (rasterMedia.exists())
                {
                    spawner.setRaster(rasterMedia);
                }
            });
        }
        if (!client)
        {
            loadEntities(settings, stage);
        }
    }

    private void loadEntities(Settings settings, StageConfig stage)
    {
        final Featurable[] entities = createEntities(settings, stage);

        if (settings.isFlagParallel())
        {
            com.b3dgs.lionengine.Parallels.get().forEach(1, i -> createEffectCache(settings, stage));
        }
        else
        {
            createEffectCache(settings, stage);
        }

        if (entities != null && RasterType.CACHE == Settings.getInstance().getRaster() && settings.isFlagParallel())
        {
            loadRasterEntities(stage, entities);
        }

        loadSpawns(settings, stage);
    }

    @Override
    public void reloadStage()
    {
        reload = true;
        tick.addAction(() ->
        {
            handler.updateAdd();
            for (final Featurable featurable : handler.values())
            {
                featurable.ifIs(Collidable.class, c ->
                {
                    if (!Constant.COLL_GROUP_PLAYER.equals(c.getGroup()))
                    {
                        featurable.getFeature(Identifiable.class).destroy();
                    }
                });
            }
            handler.updateAdd();
            for (final Featurable featurable : handler.values())
            {
                featurable.ifIs(Collidable.class, c ->
                {
                    if (!Constant.COLL_GROUP_PLAYER.equals(c.getGroup()))
                    {
                        featurable.getFeature(Identifiable.class).destroy();
                    }
                });
            }
            handler.updateRemove();
            factory.clearCache();

            final Settings settings = Settings.getInstance();
            final StageConfig stage = StageConfig.imports(new Configurer(game.getInit().getStage()));

            if (stage.getBossSpawn().isPresent())
            {
                spawn(Medias.create(Folder.BOSS, stage.getBackground().getWorld().getFolder(), "Boss.xml"),
                      stage.getBossSpawn().get().getX() * map.getTileWidth(),
                      stage.getBossSpawn().get().getY() * map.getTileHeight()).getFeature(EntityModel.class)
                                                                              .setNext(stage.getBossNext(),
                                                                                       Optional.empty());
                if (stage.getBackground().getWorld() == WorldType.DRAGONFLY)
                {
                    spawn(Medias.create(Folder.ENTITY, WorldType.DRAGONFLY.getFolder(), "Dragonflyer.xml"),
                          stage.getBoss().get().getX() * map.getTileWidth(),
                          stage.getBoss().get().getY() * map.getTileHeight());
                }
            }
            else
            {
                final Featurable[] entities = createEntities(settings, stage);
                if (entities != null
                    && RasterType.CACHE == Settings.getInstance().getRaster()
                    && settings.isFlagParallel())
                {
                    loadRasterEntities(stage, entities);
                }
            }
            handler.updateAdd();
            landscape.reset();
            reload = false;
        }, 0);
    }

    private void loadSpawns(Settings settings, StageConfig stage)
    {
        final List<SpawnConfig> configs = stage.getSpawns();
        final int n = configs.size();
        for (int i = 0; i < n; i++)
        {
            final SpawnConfig spawn = configs.get(i);
            spawnTick.addAction(() ->
            {
                final List<EntityConfig> entities = spawn.getEntities();
                final int k = entities.size();
                for (int j = 0; j < k; j++)
                {
                    final EntityConfig entity = entities.get(j);
                    final Featurable featurable = factory.create(entity.getMedia());
                    featurable.getFeature(Transformable.class).teleport(entity.getSpawnX(map), entity.getSpawnY(map));
                    Util.loadEntityFeature(featurable, entity);
                    if (RasterType.CACHE == settings.getRaster())
                    {
                        loadRasterEntity(stage, featurable);
                    }
                    handler.add(featurable);
                }
            }, source.getRate(), spawn.getDelay());
        }
    }

    /**
     * Load raster direct colors.
     * 
     * @param stage The stage reference.
     */
    private void loadRasterDirect(StageConfig stage)
    {
        rasterbar.clearRasterbarColor();

        stage.getRasterFolder().ifPresent(r ->
        {
            final Media rasterTiles = Medias.create(r, "tiles2.png");
            if (rasterTiles.exists())
            {
                rasterbar.addRasterbarColor(Graphics.getImageBuffer(rasterTiles));
            }

            final Media rasterWater = Medias.create(r, "water2.png");
            if (rasterWater.exists())
            {
                rasterbar.addRasterbarColor(Graphics.getImageBuffer(rasterWater));
            }

            final String raster;
            if (ForegroundType.LAVA == stage.getForeground().getType())
            {
                rasterbar.setRasterbarOffset(-37, 1);
                raster = "lava2.png";
            }
            else
            {
                rasterbar.setRasterbarOffset(-24, 16);
                raster = "water2.png";
            }
            final Media rasterHero = Medias.create(Folder.RASTER, Folder.HERO, "valdyn", raster);
            if (rasterHero.exists())
            {
                rasterbar.addRasterbarColor(Graphics.getImageBuffer(rasterHero));
            }
        });
        rasterbar.prepare();
        rasterRenderer = rasterbar::renderRasterbar;
    }

    /**
     * Load map from level.
     * 
     * @param settings The settings reference.
     * @param config The stage config.
     */
    private void loadMap(Settings settings, StageConfig config)
    {
        final Media media = config.getMapFile();
        if (!media.exists())
        {
            MapTileHelper.importAndSave(Medias.create(media.getPath().replace(Extension.MAP, Extension.IMAGE)),
                                        Medias.create(Folder.LEVEL,
                                                      config.getBackground().getWorld().getFolder(),
                                                      TileSheetsConfig.FILENAME),
                                        media,
                                        new MapTilePersisterOptimized());
        }

        // CHECKSTYLE IGNORE LINE: AnonInnerLength
        map.getFeature(MapTilePersister.class).addListener(new MapTilePersisterListener()
        {
            @Override
            public void notifyMapLoadStart()
            {
                map.loadBefore(map.getMedia());
            }

            @Override
            public void notifyMapLoaded()
            {
                if (settings.isFlagParallel())
                {
                    com.b3dgs.lionengine.Parallels.get().forEach(1, i -> map.loadAfter(map.getMedia()));
                }
                else
                {
                    map.loadAfter(map.getMedia());
                }
                camera.setLimits(map);
            }
        });
        final MapTileGroup mapGroup = map.getFeature(MapTileGroup.class);
        final TileSetListener tileListener = tile ->
        {
            if (CollisionName.LIANA_TOP.equals(mapGroup.getGroup(tile)))
            {
                spawn(Medias.create(Folder.EFFECT, WorldType.SWAMP.getFolder(), "Liana.xml"),
                      tile.getX() + tile.getWidth() / 2.0,
                      tile.getY());
            }
            else if (CollisionName.BLOCK.equals(mapGroup.getGroup(tile)))
            {
                spawn(Medias.create(Folder.EFFECT, WorldType.ANCIENTTOWN.getFolder(), "Block.xml"), tile);
            }
        };
        map.addListener(tileListener);

        final Optional<String> raster = config.getRasterFolder();
        if (RasterType.CACHE == settings.getRaster())
        {
            raster.ifPresent(r -> map.getFeature(MapTileRastered.class)
                                     .setRaster(Medias.create(r, Constant.RASTER_FILE_TILE),
                                                config.getLinesPerRaster(),
                                                config.getRasterLineOffset()));
        }
        map.loadSheets(Medias.create(Folder.LEVEL,
                                     config.getBackground().getWorld().getFolder(),
                                     TileSheetsConfig.FILENAME));
        Util.loadMapTiles(map, media);
        loadMapBottom(settings, config, media, raster);

        createMapCollisionDebug();

        map.removeListener(tileListener);
    }

    /**
     * Load map bottom part.
     * 
     * @param settings The settings reference.
     * @param config The stage config.
     * @param media The media reference.
     * @param raster The raster reference.
     */
    private void loadMapBottom(Settings settings, StageConfig config, Media media, Optional<String> raster)
    {
        final Media bottomRip = Medias.create(media.getPath().replace(Extension.MAP, MAP_BOTTOM + Extension.IMAGE));
        final Media bottom = Medias.create(media.getPath().replace(Extension.MAP, MAP_BOTTOM + Extension.MAP));
        if (bottom.exists() || bottomRip.exists())
        {
            if (!bottom.exists())
            {
                MapTileHelper.importAndSave(bottomRip, bottom, new MapTilePersisterOptimized());
            }
            final MapTileGame mapBottom = new MapTileGame();
            mapBottom.addFeature(new MapTilePersisterOptimized());
            mapBottom.addFeature(new MapTileGroupModel());
            mapBottom.addFeature(new MapTileCollisionModel());
            mapBottom.addFeature(new LayerableModel(4, 5));
            final MapTileViewer mapViewer = mapBottom.addFeature(new MapTileViewerModel(services));
            mapBottom.loadSheets(Medias.create(Folder.LEVEL,
                                               config.getBackground().getWorld().getFolder(),
                                               TileSheetsConfig.FILENAME));
            Util.loadMapTiles(mapBottom, bottom);

            raster.ifPresent(r ->
            {
                final MapTileRastered mapRaster = mapBottom.addFeature(new MapTileRasteredModel());
                if (mapRaster.loadSheets() && RasterType.CACHE == settings.getRaster())
                {
                    mapViewer.clear();
                    mapViewer.addRenderer(mapRaster);
                    mapRaster.setRaster(Medias.create(r, Constant.RASTER_FILE_TILE),
                                        config.getLinesPerRaster(),
                                        config.getRasterLineOffset());
                }
            });

            handler.add(mapBottom);
            loadWaterRaster(settings, config, raster, mapBottom, true);
        }
        else
        {
            loadWaterRaster(settings, config, raster, map, false);
        }
    }

    /**
     * Load raster data.
     * 
     * @param settings The settings reference.
     * @param config The stage configuration.
     * @param raster The raster folder.
     * @param map The map tile reference.
     * @param bottom The bottom flag.
     */
    private void loadWaterRaster(Settings settings,
                                 StageConfig config,
                                 Optional<String> raster,
                                 MapTile map,
                                 boolean bottom)
    {
        if (RasterType.CACHE == settings.getRaster())
        {
            final ForegroundType foreground = config.getForeground().getType();
            if (foreground == ForegroundType.WATER || foreground == ForegroundType.LAVA)
            {
                raster.ifPresent(r ->
                {
                    mapWater.create(r);
                    mapWater.addFeature(new LayerableModel(4, 3));
                    handler.add(mapWater);

                    if (bottom)
                    {
                        final MapTileWater mapWaterBottom = new MapTileWater(services, true);
                        mapWaterBottom.create(r);
                        mapWaterBottom.addFeature(new LayerableModel(4, 6));
                        handler.add(mapWaterBottom);
                    }
                });
            }
        }
    }

    /**
     * Create map tile collision debug rendering if enabled.
     */
    private void createMapCollisionDebug()
    {
        if (Constant.DEBUG_COLLISIONS)
        {
            final MapTileCollisionRenderer renderer = map.addFeature(new MapTileCollisionRendererModel());
            renderer.createCollisionDraw();

            final MapTileViewer mapViewer = map.getFeature(MapTileViewer.class);
            mapViewer.addRenderer(renderer);
        }
    }

    /**
     * Create player and track with camera.
     * 
     * @param settings The settings reference.
     * @param init The initial configuration.
     * @param stage The stage reference.
     * @return The created player.
     */
    private Featurable createPlayer(Settings settings, InitConfig init, StageConfig stage)
    {
        final Featurable featurable = factory.create(Medias.create(Folder.HERO, "valdyn", "Valdyn.xml"));
        players.add(featurable);
        handler.add(featurable);

        if (!server && !client)
        {
            featurable.getFeature(EntityModel.class).setInput(device);
            featurable.getFeature(EntityModel.class).setCamera(camera);
            featurable.getFeature(EntityModel.class).setTracker(tracker);
            featurable.getFeature(Stats.class).apply(init);
            featurable.getFeature(Stats.class).setImmortal(init.isCheats());
            player = featurable.getFeature(StateHandler.class);
            hud.setFeaturable(featurable);
        }

        final Optional<Coord> spawn = init.getSpawn();
        final Transformable transformable = featurable.getFeature(Transformable.class);
        checkpoints.register(transformable);

        if (spawn.isPresent())
        {
            transformable.teleport(spawn.get().getX() * map.getTileWidth(), spawn.get().getY() * map.getTileHeight());
        }
        else
        {
            final Coord coord = checkpoints.getCurrent(transformable);
            transformable.teleport(coord.getX(), coord.getY());
        }
        if (!server)
        {
            trackPlayer(featurable);
        }

        if (RasterType.CACHE == settings.getRaster())
        {
            if (settings.isFlagParallel())
            {
                com.b3dgs.lionengine.Parallels.get().forEach(1, i -> loadRasterHero(stage, featurable));
            }
            else
            {
                loadRasterHero(stage, featurable);
            }
        }
        return featurable;
    }

    /**
     * Track player with camera.
     * 
     * @param player The player to track.
     */
    private void trackPlayer(Featurable player)
    {
        tracker.addFeature(new LayerableModel(player.getFeature(Layerable.class).getLayerRefresh().intValue() + 1));
        trackerInitY = player.getFeature(Transformable.class).getHeight() / 2 + 8;
        tracker.setOffset(0, trackerInitY);
        tracker.track(player);
    }

    /**
     * Create player in split case and track with camera.
     * 
     * @param settings The settings reference.
     * @param init The initial configuration.
     * @param stage The stage reference.
     * @param id The player id.
     * @return The created player.
     */
    private Featurable createPlayerSplit(Settings settings, InitConfig init, StageConfig stage, int id)
    {
        final Featurable featurable = factory.create(Medias.create(Folder.HERO, "valdyn", "Valdyn" + id + ".xml"));
        players.add(featurable);
        handler.add(featurable);

        featurable.getFeature(Stats.class).apply(init);

        final Optional<Coord> spawn = init.getSpawn();
        final Transformable transformable = featurable.getFeature(Transformable.class);
        checkpoints.register(transformable);

        if (spawn.isPresent())
        {
            transformable.teleport(spawn.get().getX() * map.getTileWidth(), spawn.get().getY() * map.getTileHeight());
        }
        else
        {
            final Coord coord = checkpoints.getCurrent(transformable);
            transformable.teleport(coord.getX(), coord.getY());
        }

        if (RasterType.CACHE == settings.getRaster())
        {
            if (settings.isFlagParallel())
            {
                com.b3dgs.lionengine.Parallels.get().forEach(1, i -> loadRasterHero(stage, featurable));
            }
            else
            {
                loadRasterHero(stage, featurable);
            }
        }
        return featurable;
    }

    /**
     * Create entities from stage.
     * 
     * @param settings The settings reference.
     * @param stage The stage config.
     * @return The created entities.
     */
    private Featurable[] createEntities(Settings settings, StageConfig stage)
    {
        final Featurable[] entities;
        if (settings.isFlagParallel() && RasterType.CACHE == settings.getRaster())
        {
            final List<EntityConfig> config = stage.getEntities();
            final int n = config.size();
            entities = new Featurable[n];
            for (int i = 0; i < n; i++)
            {
                entities[i] = createEntity(config.get(i));
            }
        }
        else
        {
            entities = null;
            final List<EntityConfig> entityConfig = stage.getEntities();
            final int n = entityConfig.size();
            for (int i = 0; i < n; i++)
            {
                final Featurable featurable = createEntity(entityConfig.get(i));
                if (RasterType.CACHE == settings.getRaster())
                {
                    loadRasterEntity(stage, featurable);
                }
                handler.add(featurable);
            }
        }
        return entities;
    }

    /**
     * Create entity from configuration.
     * 
     * @param entity The entity configuration.
     * @return The created entity.
     */
    private Featurable createEntity(EntityConfig entity)
    {
        final Featurable featurable = factory.create(entity.getMedia());
        featurable.getFeature(Transformable.class).teleport(entity.getSpawnX(map), entity.getSpawnY(map));
        if (Difficulty.LIONHARD == difficulty)
        {
            featurable.ifIs(Stats.class, Stats::initLionhard);
        }
        Util.loadEntityFeature(featurable, entity);

        return featurable;
    }

    /**
     * Create entity from configuration.
     * 
     * @param stage The stage configuration.
     * @param featurable The featurable reference.
     * @return The created entity.
     */
    private Featurable loadRasterEntity(StageConfig stage, Featurable featurable)
    {
        stage.getRasterFolder().ifPresent(raster ->
        {
            featurable.ifIs(Underwater.class, underwater -> underwater.loadRaster(raster));
            featurable.ifIs(BulletBounceOnGround.class, bullet -> bullet.loadRaster(raster));

            final Media media = Medias.create(raster, Constant.RASTER_FILE_TILE);
            if (media.exists())
            {
                featurable.ifIs(Rasterable.class,
                                r -> r.setRaster(true,
                                                 media,
                                                 map.getTileHeight(),
                                                 stage.getLinesPerRaster(),
                                                 stage.getRasterLineOffset()));
            }
        });
        return featurable;
    }

    private void loadRasterEntities(StageConfig stage, Featurable[] entities)
    {
        final int threads = Runtime.getRuntime().availableProcessors();
        final int entitiesPerThread = (int) Math.floor(entities.length / (double) threads);
        int start = 0;

        final List<java.util.function.Supplier<Featurable[]>> tasks = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++)
        {
            final int end;
            if (i < threads - 1)
            {
                end = start + entitiesPerThread;
            }
            else
            {
                end = entities.length;
            }
            tasks.add(loadRasterEntities(stage, entities, start, end));
            start += entitiesPerThread;
        }

        mergeEntitiesToHandler(tasks);
    }

    private java.util.function.Supplier<Featurable[]> loadRasterEntities(StageConfig stage, Featurable[] featurables, int start, int end)
    {
        return com.b3dgs.lionengine.Parallels.get().submit(() ->
        {
            final int n = end - start;
            final Featurable[] toAdd = new Featurable[n];
            int i = 0;
            for (i = 0; i < n; i++)
            {
                final int index = start + i;
                toAdd[i] = loadRasterEntity(stage, featurables[index]);
            }
            return toAdd;
        });
    }

    private void mergeEntitiesToHandler(List<java.util.function.Supplier<Featurable[]>> tasks)
    {
        final int n = tasks.size();
        for (int i = 0; i < n; i++)
        {
            final Featurable[] toAdd = tasks.get(i).get();
            if (toAdd != null)
            {
                for (int j = 0; j < toAdd.length; j++)
                {
                    handler.add(toAdd[j]);
                    toAdd[j] = null;
                }
            }
        }
    }

    /**
     * Create effect and cache.
     * 
     * @param settings The settings reference.
     * @param stage The stage configuration.
     */
    private void createEffectCache(Settings settings, StageConfig stage)
    {
        final Spawner cacheSpawner = (media, x, y) ->
        {
            final Featurable f = factory.create(media);
            f.getFeature(Transformable.class).teleport(x, y);

            if (RasterType.CACHE == settings.getRaster())
            {
                stage.getRasterFolder().ifPresent(raster ->
                {
                    final Media rasterMedia = Medias.create(raster, Constant.RASTER_FILE_TILE);
                    if (rasterMedia.exists())
                    {
                        f.ifIs(Rasterable.class, r -> r.setRaster(true, rasterMedia, map.getTileHeight()));
                    }
                });
            }
            return f;
        };

        final String theme = stage.getBackground().getWorld().getFolder();

        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "ExplodeBig.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "ExplodeLittle.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "Explode.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "Explode5.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "ExplodeBlock.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "ExplodeLiana.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "ExplodeSilent.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "Taken.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "LaserDot.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.EFFECT, theme, "Smoke.xml"), 4);

        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Fly.xml"), 6);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "FlyTrack.xml"), 3);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Tongue.xml"), 6);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "TongueEnd.xml"), 2);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet.xml"), 5);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet1.xml"), 6);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet1a.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet1b.xml"), 6);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet2.xml"), 6);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet3.xml"), 6);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Bullet4.xml"), 3);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Fireball.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "FireballUp.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "ForeballDown.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "HotFireBall.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "HotFireBall2.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Baril.xml"), 2);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Dragon3.xml"), 4);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Laser.xml"), 3);
        factory.createCacheMedia(cacheSpawner, Medias.create(Folder.PROJECTILE, theme, "Rock.xml"), 2);
    }

    private void quickSave()
    {
        try (FileWriting file = new FileWriting(Medias.create(Constant.FILE_SNAPSHOT)))
        {
            int n = 0;
            for (final Featurable featurable : handler.values())
            {
                if (featurable.hasFeature(EntityModel.class))
                {
                    n++;
                }
            }
            file.writeInteger(n);

            saveSnapshotables(file);
        }
        catch (final IOException exception)
        {
            LOGGER.error("quickSave error", exception);
        }
    }

    private void saveSnapshotables(FileWriting file) throws IOException
    {
        for (final Featurable featurable : handler.values())
        {
            if (featurable.hasFeature(EntityModel.class))
            {
                file.writeString(featurable.getMedia().getPath());
                for (final FeatureProvider feature : featurable.getFeatures())
                {
                    if (feature instanceof final Snapshotable snapshotable)
                    {
                        snapshotable.save(file);
                    }
                }
            }
        }
    }

    private void quickLoad()
    {
        final Media media = Medias.create(Constant.FILE_SNAPSHOT);
        if (media.exists())
        {
            try (FileReading file = new FileReading(media))
            {
                removeSnapshotables();

                final int n = file.readInteger();
                for (int i = 0; i < n; i++)
                {
                    loadSnapshotable(file);
                }
                handler.updateAdd();
            }
            catch (final IOException exception)
            {
                LOGGER.error("quickLoad error", exception);
            }
        }
    }

    private void removeSnapshotables()
    {
        handler.updateAdd();
        for (final Featurable featurable : handler.values())
        {
            for (final FeatureProvider feature : featurable.getFeatures())
            {
                if (feature instanceof Snapshotable)
                {
                    featurable.getFeature(Identifiable.class).destroy();
                    break;
                }
            }
        }
        handler.updateRemove();
    }

    private void loadSnapshotable(FileReading file) throws IOException
    {
        final Featurable featurable = factory.create(Medias.create(file.readString()));
        if (featurable.hasFeature(EntityInputController.class))
        {
            hud.setFeaturable(featurable);
            player = featurable.getFeature(StateHandler.class);
            trackerInitY = player.getFeature(Transformable.class).getHeight() / 2 + 8;
            tracker.setOffset(0, trackerInitY);
            tracker.track(player);
        }
        for (final FeatureProvider feature : featurable.getFeatures())
        {
            if (feature instanceof final Snapshotable snapshotable)
            {
                snapshotable.load(file);
            }
        }
        handler.add(featurable);
    }

    private void updateSpawn(double extrp)
    {
        spawnTick.update(extrp);
    }

    private void updateZoom(double extrp)
    {
        if (device.isFired(DeviceMapping.ZOOM_OUT))
        {
            zoom.setDestination(ZOOM_OUT_MAX, 0.0);

        }
        else if (device.isFired(DeviceMapping.ZOOM_IN))
        {
            zoom.setDestination(ZOOM_IN_MAX, 0.0);
        }
        else
        {
            zoom.setDestination(zoomPref, 0.0);
        }
        final double zoomOld = zoom.getDirectionHorizontal();
        zoom.update(extrp);
        if (Double.compare(zoomOld, zoom.getDirectionHorizontal()) != 0)
        {
            zooming.setZoom(zoom.getDirectionHorizontal());
        }
    }

    /**
     * Load the stage from configuration.
     * 
     * @param init The initial configuration.
     */
    public void load(InitConfig init)
    {
        try
        {
            if (debug)
            {
                Medias.create(Constant.FILE_SNAPSHOT).getFile().delete();
            }

            hud.load();
            for (int i = 0; i < splitHud.length; i++)
            {
                splitHud[i].load();
            }

            if (game.getType().is(GameType.SPEEDRUN))
            {
                hud.setVisibleHealth(false);
                hud.setVisibleTalisment(false);
                hud.setVisibleLife(false);

                for (int i = 0; i < splitHud.length; i++)
                {
                    splitHud[i].setVisibleHealth(false);
                    splitHud[i].setVisibleTalisment(false);
                    splitHud[i].setVisibleLife(false);
                }
            }
            if (game.getType().is(GameType.BATTLE, GameType.VERSUS))
            {
                hud.setVisibleTalisment(false);
                hud.setVisibleLife(false);

                for (int i = 0; i < splitHud.length; i++)
                {
                    splitHud[i].setVisibleTalisment(false);
                    splitHud[i].setVisibleLife(false);
                }
            }

            difficulty = init.getDifficulty();

            services.add(init.getStage());

            loadStage(Settings.getInstance(), init);

            cheats.init(player, difficulty, init.isCheats());

            if (game.getType().is(GameType.SPEEDRUN, GameType.BATTLE, GameType.VERSUS))
            {
                final AtomicInteger dead = new AtomicInteger();
                final StatsDeadListener listener = () ->
                {
                    if (game.getType().is(GameType.VERSUS))
                    {
                        if (dead.incrementAndGet() == players.size() - 1)
                        {
                            for (int i = 0; i < players.size(); i++)
                            {
                                if (players.get(i).getFeature(Stats.class).getHealth() > 0)
                                {
                                    players.get(i).getFeature(Stats.class).win();
                                }
                            }
                            playMusic(Music.BOSS_WIN);
                            spawnTick.addAction(() -> sequencer.end(MenuGame.class, game.with((InitConfig) null)),
                                                source.getRate(),
                                                VERSUS_WIN_DELAY_MS);
                        }
                    }
                    else if (dead.incrementAndGet() == players.size())
                    {
                        spawnTick.addAction(() -> sequencer.end(MenuGame.class, game.with((InitConfig) null)),
                                            source.getRate(),
                                            ALLDEAD_DELAY_MS);
                    }
                };
                for (int i = 0; i < players.size(); i++)
                {
                    players.get(i).getFeature(Stats.class).addListener(listener);
                }
            }
        }
        finally
        {
            // nothing to shut down: parallelism is provided by the platform backend
        }
        // the tasks above already ran to completion through the platform backend

        handler.updateRemove();
        handler.updateAdd();

        Sfx.cacheEnd();

        if (game.getType() == GameType.SPEEDRUN)
        {
            hud.timeStart();
            for (int i = 0; i < splitHud.length; i++)
            {
                splitHud[i].timeStart();
            }
        }
        tick.restart();
        spawnTick.start();
    }

    @Override
    public void loadNextStage(String next, int delayMs, Optional<Coord> spawn)
    {
        if (game.getType().is(GameType.STORY))
        {
            if (delayMs > 0)
            {
                player.getFeature(Stats.class).win();
                tick.addAction(() -> sequencer.end(SceneBlack.class,
                                                   game.with(Util.getInitConfig(Medias.create(next),
                                                                                player,
                                                                                difficulty,
                                                                                cheats.isEnabled(),
                                                                                spawn))),
                               source.getRate(),
                               delayMs);
            }
            else
            {
                sequencer.end(SceneBlack.class,
                              game.with(Util.getInitConfig(Medias.create(next),
                                                           player,
                                                           difficulty,
                                                           cheats.isEnabled(),
                                                           spawn)));
            }
        }
        else
        {
            sequencer.end(MenuGame.class, game.with((InitConfig) null));
        }
    }

    @Override
    public void playMusic(Media media)
    {
        musicToPlay.offer(() ->
        {
            if (music != null)
            {
                music.stop();
            }
            music = AudioFactory.loadAudio(media);

            final Settings settings = Settings.getInstance();
            if (settings.getVolumeMaster() > 0)
            {
                music.setVolume(settings.getVolumeMusic());
                music.play();
            }
        });
        playNextMusicTask();
    }

    @Override
    public void stopMusic()
    {
        musicToPlay.clear();
        if (music != null)
        {
            music.stop();
            music = null;
        }
    }

    @Override
    public void update(double extrp)
    {
        updateSpawn(extrp);

        if (server)
        {
            camera.setIntervals(0, 0);
            camera.moveLocation(extrp, device.getHorizontalDirection() * 4, device.getVerticalDirection() * 4);
        }

        if (splitDevice.length > 0)
        {
            for (int i = 0; i < splitDevice.length; i++)
            {
                splitDevice[i].update(extrp);
            }
        }
        else
        {
            device.update(extrp);
        }

        cheats.update(extrp);

        if (!cheats.isPaused())
        {
            tick.update(extrp);
            super.update(extrp);
            checkpoints.update(extrp);
            landscape.update(extrp, camera);
            updateZoom(extrp);

            if (trackerY > 0)
            {
                trackerY = UtilMath.clamp(trackerY += 0.5 * extrp, 0.0, 21.0);
                tracker.setOffset(0, trackerInitY + (int) Math.floor(trackerY));
            }
            for (int i = 0; i < splitLandscape.length; i++)
            {
                splitLandscape[i].update(extrp, splitCamera[i]);

                if (splitTrackerY[i] > 0)
                {
                    splitTrackerY[i] = UtilMath.clamp(splitTrackerY[i] += 0.5 * extrp, 0.0, 21.0);
                    splitTracker[i].setOffset(0, splitTrackerInitY[i] + (int) Math.floor(splitTrackerY[i]));
                }
                splitHud[i].update(extrp);
                sequencer.setSplit(i + 1);
                rasterbar.setRasterbarY((int) splitCamera[i].getY(), (int) mapWater.getCurrent() - 2);
            }
        }
        hud.update(extrp);
        sequencer.setSplit(0);
        rasterbar.setRasterbarY((int) camera.getY(), (int) mapWater.getCurrent() - 2);

        if (debug)
        {
            if (device.isFiredOnce(DeviceMapping.QUICK_SAVE))
            {
                quickSave();
            }
            if (device.isFiredOnce(DeviceMapping.QUICK_LOAD))
            {
                quickLoad();
            }
        }
    }

    @Override
    public void render(Graphic g)
    {
        if (!reload)
        {
            landscape.renderBackground(g);

            super.render(g);

            rasterRenderer.execute();
            landscape.renderForeground(g);
            hud.render(g);

            if (device.isFired(DeviceMapping.TAB))
            {
                int y = 20;
                for (final String name : clients.values())
                {
                    text.draw(g, 2, y, Align.LEFT, name);
                    y += text.getHeight();
                }
            }
            cheats.render(g);
        }
    }

    /**
     * Render dedicated split.
     * 
     * @param g The graphic output.
     * @param split The split index.
     */
    public void render(Graphic g, int split)
    {
        if (split < game.getPlayers() - 1)
        {
            camera.setSplit(split + 1);
            sequencer.setSplit(split + 1);

            splitLandscape[split].renderBackground(g);
            super.render(g);
            rasterRenderer.execute();
            splitLandscape[split].renderForeground(g);
            splitHud[split].render(g);

            camera.setSplit(0);
            sequencer.setSplit(0);
        }
        else
        {
            g.clear(0, 0, source.getWidth(), source.getHeight());
            splitNone.setLocation(source.getWidth() / 2, source.getHeight() / 2);
            splitNone.render(g);
        }
    }

    @Override
    public void onResolutionChanged(int width, int height)
    {
        camera.setView(0, 0, width, height, height);
        camera.setLimits(map);
        cheats.onResolutionChanged(width, height);
        landscape.setScreenSize(width, height);
        hud.setScreenSize(width, height);

        for (int i = 0; i < splitCamera.length; i++)
        {
            splitCamera[i].setView(0, 0, width, height, height);
            splitCamera[i].setLimits(map);
            splitLandscape[i].setScreenSize(width, height);
            splitHud[i].setScreenSize(width, height);
        }
    }
}
