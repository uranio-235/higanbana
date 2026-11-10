package local.higanbana.infrastructure.image;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class DemoImageProvider implements ImageProvider {
    @Override
    public ImageResult generate(String prompt) {
        int offset = Math.floorMod(prompt.hashCode(), 100);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="900" height="650" viewBox="0 0 900 650">
                <defs><pattern id="tone" width="8" height="8" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1" fill="#777"/></pattern></defs>
                <rect width="900" height="650" fill="#fff"/>
                <rect x="20" y="20" width="860" height="610" fill="url(#tone)" stroke="#171717" stroke-width="6"/>
                <path d="M25 400 L260 210 L470 390 L680 160 L875 370 V625 H25Z" fill="#eee" stroke="#222" stroke-width="6"/>
                <path d="M25 520 Q340 320 875 510 V625 H25Z" fill="#fff" stroke="#222" stroke-width="5"/>
                <g stroke="#111" stroke-width="5"><path d="M80 80L320 280 M150 50L350 260 M820 80L610 270 M850 150L650 300"/></g>
                <g transform="translate(%d 0)">
                <path d="M365 600 L385 425 Q430 380 475 425 L505 600Z" fill="#222"/>
                <ellipse cx="432" cy="341" rx="57" ry="74" fill="#fff" stroke="#111" stroke-width="5"/>
                <path d="M373 347 Q355 240 443 256 Q515 250 493 355 L464 300 L445 332 L427 292 L398 338Z" fill="#111"/>
                <path d="M397 348L416 344 M448 344L468 348 M422 382Q434 388 446 380" stroke="#111" stroke-width="4" fill="none"/>
                <path d="M390 416L430 450L478 414 M430 450L434 598" stroke="#fff" stroke-width="4" fill="none"/>
                </g>
                <rect x="42" y="43" width="325" height="47" fill="#fff" stroke="#111" stroke-width="3"/>
                <text x="57" y="74" font-family="sans-serif" font-size="23" fill="#111">DEMO - ILUSTRACION LOCAL</text>
                <rect x="575" y="553" width="275" height="50" fill="#fff" stroke="#111" stroke-width="3"/>
                <text x="591" y="585" font-family="sans-serif" font-size="21">Sin generacion de imagen IA</text>
                </svg>
                """.formatted(offset - 50);
        return new ImageResult(svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml", "DEMO - ilustracion local, no IA");
    }
}