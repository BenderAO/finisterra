package shared.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.esotericsoftware.minlog.Log;
import position.WorldPos;
import shared.model.loaders.MapLoader;
import shared.model.map.Map;
import shared.model.map.Tile;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import static com.artemis.E.E;

public class MapHelper {

    public static final int NEAR_MAX_DISTNACE = 20;
    public static final int BOTTOM_BORDER_TILE = 93;
    public static final int TOP_BORDER_TILE = 8;
    public static final int LEFT_BORDER_TILE = 10;
    public static final int RIGHT_BORDER_TILE = 91;
    private static MapHelper instance;
    private HashMap<Integer, Map> maps;
    private HashMap<Map, HashMap<Dir, Integer>> surroundingMaps = new HashMap<>();

    private MapHelper() {
    }

    public static MapHelper instance() {
        if (instance == null) {
            instance = new MapHelper();
            instance.maps = new HashMap<>();
            instance.initializeMaps(instance.maps);
        }
        return instance;
    }

    public HashMap<Integer, Map> getMaps() {
        return maps;
    }

    public boolean hasMap(int mapNumber) {
        return maps.containsKey(mapNumber);
    }

    public boolean isBlocked(Map map, WorldPos pos) {
        return isBlocked(map, pos.x, pos.y);
    }

    public boolean isBlocked(Map map, int x, int y) {
        Tile tile = map.getTile(x, y);
        return tile != null && tile.isBlocked();
    }

    public boolean hasEntity(Set<Integer> entities, WorldPos pos) {
        return entities.stream().anyMatch(entity -> {
            boolean isObject = E(entity).hasObject();
            boolean isFootPrint = E(entity).hasFootprint();
            boolean samePos = E(entity).hasWorldPos() && pos.equals(E(entity).getWorldPos());
            boolean hasSameDestination = E(entity).hasMovement() && E(entity).getMovement().destinations.stream().anyMatch(destination -> destination.worldPos.equals(pos));
            return !isObject && !isFootPrint && (samePos || hasSameDestination);
        });
    }

    /**
     * Initialize maps.
     */
    public void initializeMaps(HashMap<Integer, Map> maps) {
        Log.info("Loading maps...");
        getAlkonMaps(maps);
    }

    public void getAlkonMaps(HashMap<Integer, Map> maps) {
        for (int i = 1; i <= 290; i++) {
            Map map = getMap(i);
            maps.put(i, map);
        }
    }

    public Map getMap(int i) {
        if (hasMap(i)) {
            return maps.get(i);
        }
        FileHandle mapPath = Gdx.files.internal(SharedResources.MAPS_FOLDER + "Alkon/Mapa" + i + ".map");
        FileHandle infPath = Gdx.files.internal(SharedResources.MAPS_FOLDER + "Alkon/Mapa" + i + ".inf");
        MapLoader loader = new MapLoader();
        try (DataInputStream map = new DataInputStream(mapPath.read());
             DataInputStream inf = new DataInputStream(infPath.read())) {
            return loader.load(map, inf);
        } catch (IOException | GdxRuntimeException e) {
            e.printStackTrace();
            Log.info("Failed to read map " + i);
            return new Map();
        }
    }

    public boolean hasTileExit(Map map, WorldPos expectedPos) {
        Tile tile = map.getTile(expectedPos.x, expectedPos.y);
        return tile != null && tile.getTileExit() != null;
    }

    public Tile getTile(Map map, WorldPos pos) {
        if (pos.x > 0 && pos.x < map.getWidth()) {
            if (pos.y > 0 && pos.y < map.getHeight()) {
                return map.getTile(pos.x, pos.y);
            }
        }
        return null;
    }

    public boolean isNear(WorldPos pos1, WorldPos pos2) {
        if (pos1 == null || pos2 == null) {
            return false;
        }
        if (pos1.map == pos2.map) {
            return Math.abs(pos1.x - pos2.x) + Math.abs(pos1.y - pos2.y) < NEAR_MAX_DISTNACE;
        }
        // get distance from pos1 to pos2
        int distance = getDistanceBetweenMaps(pos1, pos2);

        return distance >= 0 &&  distance < NEAR_MAX_DISTNACE;
    }

    private int getDistanceBetweenMaps(WorldPos pos, WorldPos target) {
        int mapTarget = target.map;
        int mapNumber = pos.map;
        Map map = getMap(mapNumber);
        // TODO implement distance when 3 maps are involved
        Optional<Dir> dirTo = Arrays.stream(Dir.values()).filter(dir -> getMap(dir, map) == mapTarget).findFirst();
        return dirTo.map(dir -> getDistanceToTarget(pos, dir, target)).orElse(-1);
    }

    private int getDistanceToTarget(WorldPos pos, Dir dir, WorldPos target) {
        switch (dir) {
            case UP:
                return Math.abs(pos.y - TOP_BORDER_TILE) + Math.abs(BOTTOM_BORDER_TILE - target.y) + Math.abs(pos.x - target.x);
            case DOWN:
                return Math.abs(BOTTOM_BORDER_TILE - pos.y) + Math.abs(target.y - TOP_BORDER_TILE) + Math.abs(pos.x - target.x);
            case LEFT:
                return Math.abs(pos.x - LEFT_BORDER_TILE) + Math.abs(RIGHT_BORDER_TILE - target.x) + Math.abs(pos.y - target.y);
            case RIGHT:
                return Math.abs(RIGHT_BORDER_TILE - pos.x) + Math.abs(target.x - LEFT_BORDER_TILE) + Math.abs(pos.y - target.y);
        }
        return -1;
    }

    public int getMap(Dir dir, Map map) {
        if (surroundingMaps.containsKey(map)) {
            if (surroundingMaps.get(map).containsKey(dir)) {
                return surroundingMaps.get(map).get(dir);
            }
        }
        for (int x = 1; x < map.MAX_MAP_SIZE_WIDTH; x++) {
            for (int y = 1; y < map.MAX_MAP_SIZE_HEIGHT; y++) {
                Tile tile = map.getTile(x, y);
                if (tile != null && tile.getTileExit() != null) {
                    switch (dir) {
                        case DOWN:
                            if (y > map.MAX_MAP_SIZE_HEIGHT / 2) {
                                Optional<Tile> right = getTileWithExit(Dir.RIGHT, x, y, map);
                                Optional<Tile> left = getTileWithExit(Dir.LEFT, x, y, map);
                                Tile tileWithExit = right.orElse(left.orElse(null));
                                if (tileWithExit != null && tileWithExit.getTileExit().getMap() == tile.getTileExit().getMap()) {
                                    surroundingMaps.computeIfAbsent(map, m -> new HashMap<>()).put(dir, tile.getTileExit().getMap());
                                    return tile.getTileExit().getMap();
                                }
                            }
                            break;
                        case RIGHT:
                            if (x > map.MAX_MAP_SIZE_WIDTH / 2) {
                                Optional<Tile> up = getTileWithExit(Dir.UP, x, y, map);
                                Optional<Tile> down = getTileWithExit(Dir.DOWN, x, y, map);
                                Tile tileWithExit = up.orElse(down.orElse(null));
                                if (tileWithExit != null && tileWithExit.getTileExit().getMap() == tile.getTileExit().getMap()) {
                                    surroundingMaps.computeIfAbsent(map, m -> new HashMap<>()).put(dir, tile.getTileExit().getMap());
                                    return tile.getTileExit().getMap();
                                }
                            }
                            break;
                        case LEFT:
                            if (x < map.MAX_MAP_SIZE_WIDTH / 2) {
                                Optional<Tile> up = getTileWithExit(Dir.UP, x, y, map);
                                Optional<Tile> down = getTileWithExit(Dir.DOWN, x, y, map);
                                Tile tileWithExit = up.orElse(down.orElse(null));
                                if (tileWithExit != null && tileWithExit.getTileExit().getMap() == tile.getTileExit().getMap()) {
                                    surroundingMaps.computeIfAbsent(map, m -> new HashMap<>()).put(dir, tile.getTileExit().getMap());
                                    return tile.getTileExit().getMap();
                                }
                            }
                            break;
                        case UP:
                            if (y < map.MAX_MAP_SIZE_HEIGHT / 2) {
                                Optional<Tile> right = getTileWithExit(Dir.RIGHT, x, y, map);
                                Optional<Tile> left = getTileWithExit(Dir.LEFT, x, y, map);
                                Tile tileWithExit = right.orElse(left.orElse(null));
                                if (tileWithExit != null && tileWithExit.getTileExit().getMap() == tile.getTileExit().getMap()) {
                                    surroundingMaps.computeIfAbsent(map, m -> new HashMap<>()).put(dir, tile.getTileExit().getMap());
                                    return tile.getTileExit().getMap();
                                }
                            }
                            break;
                    }
                }
            }
        }
        surroundingMaps.computeIfAbsent(map, m -> new HashMap<>()).put(dir, -1);
        return -1;
    }

    private Optional<Tile> getTileWithExit(Dir dir, int x, int y, Map map) {
        int x2 = dir.equals(Dir.LEFT) ? x - 1 : dir.equals(Dir.RIGHT) ? x + 1 : x;
        int y2 = dir.equals(Dir.UP) ? y - 1 : dir.equals(Dir.DOWN) ? y + 1 : y;
        if (x2 < 0 || y2 < 0 || x2 >= Map.MAX_MAP_SIZE_WIDTH || y2 >= Map.MAX_MAP_SIZE_HEIGHT) {
            return Optional.empty();
        }
        Tile tile = map.getTile(x2, y2);
        return tile != null && tile.getTileExit() != null ? Optional.ofNullable(tile) : Optional.empty();
    }

    public WorldPos getEffectivePosition(int mapNumber, int x, int y) {

        int effectiveMap = mapNumber;
        if (x < LEFT_BORDER_TILE) {
            Map map = getMap(effectiveMap);
            effectiveMap = getMap(Dir.LEFT, map);
            x = RIGHT_BORDER_TILE + x - LEFT_BORDER_TILE + 1;
        } else if (x > RIGHT_BORDER_TILE) {
            Map map = getMap(effectiveMap);
            effectiveMap = getMap(Dir.RIGHT, map);
            x = x - RIGHT_BORDER_TILE + LEFT_BORDER_TILE - 1;
        }
        if (y < TOP_BORDER_TILE) {
            Map map = getMap(effectiveMap);
            effectiveMap = getMap(Dir.UP, map);
            y = BOTTOM_BORDER_TILE + y - TOP_BORDER_TILE + 1;
        } else if (y > BOTTOM_BORDER_TILE) {
            Map map = getMap(effectiveMap);
            effectiveMap = getMap(Dir.DOWN, map);
            y = y - BOTTOM_BORDER_TILE + TOP_BORDER_TILE - 1;
        }
        WorldPos effectivePos = new WorldPos(x, y, effectiveMap);
        return effectivePos;
    }

    public enum Dir {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }
}
