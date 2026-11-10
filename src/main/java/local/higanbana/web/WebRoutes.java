package local.higanbana.web;

public final class WebRoutes {
    private WebRoutes() {}

    public static String novel(long novelId, String view) {
        return "redirect:/novels/" + novelId + "/" + view;
    }
}