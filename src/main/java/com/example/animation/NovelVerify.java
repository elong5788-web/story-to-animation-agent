package com.example.animation;

/**
 * 读小说验证(用真实复杂文本《雪中悍刀行》片段,检验角色卡和场景是否精简)。
 */
public class NovelVerify {

    public static void main(String[] args) throws Exception {
        String novel = """
                徐凤年身体重重坠落在地面上，挣扎着坐起身，竟是再也站不起来，拿过身边的春雷，盘腿而坐，横放于膝。
                口中涌出鲜血已经转乌黑，不去擦拭，反正注定也擦不干净，徐凤年只是伸手揉了揉以发系发的发髻，身体发肤受之父母。
                他自幼被李义山笑称有一副富贵的北人南相，难怪投胎在徐家。大姐徐芝虎也总打趣说家里四个，就数他长得最像娘亲。
                徐凤年视线模糊，脑海走马观花，想起了许多琐碎小事，想起了徐骁伛偻背影，姐弟四人的嬉笑打闹，想起了清凉山凉王府的镇灵歌，那一袭从小就是心中浓重阴影的白衣，想起了羊皮裘老头的剑来与人去。
                太多人太多事，一闪而逝，不知为何，人生临了，除了觉得对不住宠溺自己的老爹徐骁，最后，只是想起了一名女子的酒窝，他与她，虽然一同长大，可称不上诗情画意的青梅竹马。
                徐凤年想着她的酒窝，摇晃站起身。他就算不承认，也知道自己喜欢她。既然喜欢了，却没能说出口，那就别死在这里！
                徐凤年睁眼以后，拿袖口抹了抹血污，笑着喊道：“姜泥！老子喜欢你！”
                拓跋春隼冷笑不止，只不过再一次笑不出来。一名年轻女子御剑而来，身后有青衫儒士凌波微步，逍遥踏空。女子站在一柄长剑之上，在身陷必死之地的家伙身前悬空。她瞪眼怒道：“喊我做什么？不要脸！”
                """;

        DeepSeekClient ds = new DeepSeekClient();
        Context ctx = new Context(novel);

        NovelBreakdown b = new NovelParser(ds).generateOnce(ctx, null);
        ctx.setNovelBreakdown(b);
        System.out.println("===== 故事拆解 =====");
        System.out.println("画风: " + b.style());
        System.out.println("角色: " + b.characters());
        System.out.println("世界观: " + b.world().toCoreText());
        System.out.println("情节分段(" + b.segments().size() + "段):");
        for (int i = 0; i < b.segments().size(); i++) {
            System.out.println("  " + (i + 1) + ". " + b.segments().get(i));
        }

        StoryBoard board = new StoryboardDesigner(ds, new Retriever()).generateOnce(ctx, null);
        System.out.println("\n===== 多镜头分镜(" + board.shots().size() + "镜头) =====");
        for (int i = 0; i < board.shots().size(); i++) {
            Shot s = board.shots().get(i);
            System.out.println("镜头" + (i + 1) + " [" + s.time() + "] " + s.framing());
            System.out.println("  场景:" + s.scene());
            System.out.println("  动作:" + s.action());
            System.out.println("  运镜:" + s.camera() + " | 情绪:" + s.emotion());
            System.out.println("  画质:" + s.quality());
        }
    }
}
