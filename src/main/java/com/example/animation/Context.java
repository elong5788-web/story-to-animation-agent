package com.example.animation;

/**
 * 流水线累积状态:每步的产出写回这里,供后续步骤读取。
 * 封装了读写和"取消"状态,不再暴露 public 字段。
 */
public class Context {
    private final String input;
    private Localization loc;
    private WorldBuilding world;
    private ShotDesign design;
    private NovelBreakdown novelBreakdown;
    private StoryBoard storyBoard;
    private boolean cancelled;

    public Context(String input) {
        this.input = input;
    }

    public String input() {
        return input;
    }

    public Localization loc() {
        return loc;
    }

    public void setLoc(Localization loc) {
        this.loc = loc;
    }

    public WorldBuilding world() {
        return world;
    }

    public void setWorld(WorldBuilding world) {
        this.world = world;
    }

    public ShotDesign design() {
        return design;
    }

    public void setDesign(ShotDesign design) {
        this.design = design;
    }

    public NovelBreakdown novelBreakdown() {
        return novelBreakdown;
    }

    public void setNovelBreakdown(NovelBreakdown b) {
        this.novelBreakdown = b;
    }

    public StoryBoard storyBoard() {
        return storyBoard;
    }

    public void setStoryBoard(StoryBoard b) {
        this.storyBoard = b;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    /** 带定位信息的上下文文本(给世界观/分镜当 user message 用) */
    public String withLoc() {
        return input + "\n\n[定位信息]\n" + loc.toText();
    }
}
