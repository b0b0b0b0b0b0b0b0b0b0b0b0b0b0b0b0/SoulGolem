package bm.b0b0b0.SoulGolem.config.settings;

import java.util.ArrayList;
import java.util.List;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class Settings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public Settings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Server/plugin settings. Golem behavior (farmer/miner/energy/ores): golems.yml"),
            @CommentValue("Language file name without extension (lang/<name>.yml)")
    })
    public String language = "ru";

    @NewLine
    @Comment({
            @CommentValue("Maximum active golems per player (permission overrides possible later)")
    })
    public int maxGolemsPerPlayer = 3;

    @NewLine
    @Comment({
            @CommentValue("Blocks where a statue can be placed to spawn a golem")
    })
    public List<String> placementBlocks = defaultPlacement();

    @NewLine
    @Comment({
            @CommentValue("Activation: experience levels required (0 = free)")
    })
    public int activationXpLevels = 0;

    @Comment({
            @CommentValue("Activation item costs as MATERIAL:amount (empty = none)")
    })
    public List<String> activationItems = new ArrayList<>();

    @Comment({
            @CommentValue("Prevent spawning when work area overlaps another golem")
    })
    public boolean preventAreaOverlap = true;

    @Comment({
            @CommentValue("Extra gap (blocks) between golem areas: homes closer than radiusA + radiusB + this are rejected")
    })
    public int areaOverlapPadding = 3;

    @NewLine
    public Permissions permissions = new Permissions();

    @NewLine
    public Database database = new Database();

    public static final class Permissions {
        public String admin = "soulgolem.admin";
        public String use = "soulgolem.use";
        public String give = "soulgolem.give";
        public String reload = "soulgolem.reload";
        public String bypassLimit = "soulgolem.bypass.limit";
    }

    public static final class Database {
        @Comment({
                @CommentValue("sqlite or mysql")
        })
        public String type = "sqlite";

        @Comment({
                @CommentValue("SQLite path relative to plugin folder (e.g. db/data.db)")
        })
        public String sqliteFile = "db/data.db";

        public String mysqlHost = "localhost";
        public int mysqlPort = 3306;
        public String mysqlDatabase = "soulgolem";
        public String mysqlUsername = "root";
        public String mysqlPassword = "password";

        public int poolSize = 4;
        public long connectionTimeoutMs = 10000L;
    }

    private static List<String> defaultPlacement() {
        List<String> list = new ArrayList<>();
        list.add("GRASS_BLOCK");
        return list;
    }
}
