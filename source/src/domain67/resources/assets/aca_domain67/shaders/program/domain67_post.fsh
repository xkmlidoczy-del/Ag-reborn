#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;
uniform float PulseTime, ImpactFrame, ShockStrength, ShockRing, FocusUvX, FocusUvY, ChromaticBoost, BloomStrength;
uniform float DomainStrength, DomainRadius, StageRadius, ShatterProgress, ViewerInside, UrgencyPulse;
uniform float FieldCenterX, FieldCenterY, FieldCenterZ;
uniform float Inv0, Inv1, Inv2, Inv3, Inv4, Inv5, Inv6, Inv7;
uniform float Inv8, Inv9, Inv10, Inv11, Inv12, Inv13, Inv14, Inv15;

in vec2 texCoord;
out vec4 fragColor;

// Expanded 320m Infinite Warm Cream-White, Honey-Oak & Champagne-Gold Modern Luxury Banquet Hall
const float BAY_SIZE = 16.0;
const float HALF_BAY = 8.0;
const float CEIL_Y = 21.0;
const float MAX_RAY_DIST = 320.0;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    vec3 a = fract(p.xyx * vec3(123.34, 234.34, 345.65));
    a += dot(a, a + 34.45);
    return fract(vec2(a.x * a.y, a.y * a.z));
}

// Voronoi Reality-Glass Shards for Beat-2 Opening Fracture & Collapse Dissolution
vec2 voronoiGlass(vec2 x) {
    vec2 n = floor(x);
    vec2 f = fract(x);
    float md = 8.0;
    float md2 = 8.0;
    float cid = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash22(n + g);
            vec2 r = g + o - f;
            float d = dot(r, r);
            if (d < md) {
                md2 = md;
                md = d;
                cid = hash21(n + g + vec2(7.1, 3.3));
            } else if (d < md2) {
                md2 = d;
            }
        }
    }
    return vec2(sqrt(md2) - sqrt(md), cid);
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdTorus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

// 3D Signed Distance Field of the Infinite Warm Ivory-White & Champagne-Gold Modern Luxury Banquet Hall
// Returns vec2(distance, materialId):
// 1.0 = Fluted Cream-Marble & Gold Column, 2.0 = Modern Sunlit Coffered Arch,
// 3.0 = Cream-Leather & Honey-Oak Booth, 4.0 = 3000K Crystal-Gold Chandelier & Warm Lamp
vec2 mapModernRestaurantHall(vec3 p) {
    float clearCarve = (StageRadius + 0.75) - length(p.xz);

    vec2 cell = p.xz - BAY_SIZE * floor((p.xz + HALF_BAY) / BAY_SIZE);
    vec2 ac = abs(cell);

    // 1. Fluted Warm Cream-Marble & Brushed-Gold Columns at every 16m bay corner
    vec2 colP = ac - vec2(6.85, 6.85);
    float colShaft = length(max(abs(colP) - vec2(0.74), 0.0)) - 0.14;
    float colPlinth = sdBox(vec3(colP.x, p.y - 0.65, colP.y), vec3(1.06, 0.65, 1.06));
    float colCap = sdBox(vec3(colP.x, p.y - 15.6, colP.y), vec3(1.14, 0.52, 1.14));
    float dCol = min(colShaft, min(colPlinth, colCap));

    // 2. Graceful Modern Rounded Coffered Vault Arches spanning between columns (y in 13.8..21.0)
    float archSpanX = clamp(ac.x / 6.85, 0.0, 1.0);
    float archCurveX = 14.6 + 4.8 * sqrt(max(0.0, 1.0 - archSpanX * archSpanX));
    float archBeamZ = max(ac.y - 7.20, 6.50 - ac.y);
    float dArchX = max(archBeamZ, archCurveX - p.y);

    float archSpanZ = clamp(ac.y / 6.85, 0.0, 1.0);
    float archCurveZ = 14.6 + 4.8 * sqrt(max(0.0, 1.0 - archSpanZ * archSpanZ));
    float archBeamX = max(ac.x - 7.20, 6.50 - ac.x);
    float dArchZ = max(archBeamX, archCurveZ - p.y);
    float dArch = max(min(dArchX, dArchZ), 13.8 - p.y);

    // 3. Suspended Double-Ring Crystal & Champagne-Gold Chandelier in every 16m bay
    float dRingOuter = sdTorus(vec3(cell.x, p.y - 14.2, cell.y), vec2(2.75, 0.14));
    float dRingInner = sdTorus(vec3(cell.x, p.y - 13.3, cell.y), vec2(1.55, 0.11));
    float dRod = max(length(cell) - 0.08, abs(p.y - 17.2) - 3.6);
    float dLight = min(min(dRingOuter, dRingInner), dRod);

    // 4. Receding Modern Cream-Leather & Honey-Oak Dining Booths outside grand cross-aisles
    float dBooth = 99.0;
    if (abs(p.x) > 4.6 && abs(p.z) > 4.6 && p.y < 2.5) {
        float sofa = sdBox(vec3(cell.x, p.y - 0.65, abs(cell.y) - 1.75), vec3(1.85, 0.65, 0.44));
        float table = sdBox(vec3(cell.x, p.y - 0.46, cell.y), vec3(1.65, 0.46, 0.72));
        float lamp = length(vec3(cell.x - 1.05, p.y - 1.28, cell.y)) - 0.22;
        dLight = min(dLight, lamp);
        dBooth = min(sofa, table);
    }

    vec2 res = vec2(dCol, 1.0);
    if (dArch < res.x) res = vec2(dArch, 2.0);
    if (dBooth < res.x) res = vec2(dBooth, 3.0);
    if (dLight < res.x) res = vec2(dLight, 4.0);

    res.x = max(res.x, clearCarve);
    return res;
}

vec3 calcHallNormal(vec3 p) {
    const vec2 e = vec2(0.035, -0.035);
    return normalize(
        e.xyy * mapModernRestaurantHall(p + e.xyy).x +
        e.yyx * mapModernRestaurantHall(p + e.yyx).x +
        e.yxy * mapModernRestaurantHall(p + e.yxy).x +
        e.xxx * mapModernRestaurantHall(p + e.xxx).x
    );
}

// Evaluates the Warm White Coffered Architectural Ceiling & 3000K Recessed Cove Skylights (100% Flicker-Free!)
vec3 evalModernCofferedCeiling(vec3 p) {
    vec2 cell = p.xz - BAY_SIZE * floor((p.xz + HALF_BAY) / BAY_SIZE);
    vec2 ac = abs(cell);
    float maxC = max(ac.x, ac.y);
    float r = length(cell);

    // Warm Venetian Cream-White Plaster Base
    vec3 plaster = mix(vec3(0.99, 0.965, 0.91), vec3(0.95, 0.90, 0.80), smoothstep(1.5, 7.2, maxC));

    // Recessed 3000K Warm Golden Linear Cove Light Perimeter in each 16m Coffer
    float coveBand = smoothstep(5.95, 5.55, maxC) * smoothstep(4.85, 5.25, maxC);
    float goldMolding = smoothstep(0.24, 0.05, abs(maxC - 6.85));

    // Central Circular Sunlit Alabaster Skylight Dome + Brushed-Gold Medallion Ring
    float skylight = smoothstep(3.2, 2.5, r);
    float medRing = smoothstep(3.45, 3.20, r) * smoothstep(2.90, 3.15, r);

    vec3 col = plaster
             + vec3(1.00, 0.90, 0.62) * (coveBand * 0.52 + skylight * 0.45)
             + vec3(0.95, 0.78, 0.35) * (goldMolding * 0.45 + medRing * 0.55);
    return col;
}

// Analytic Box-Filtered Anti-Aliased Checkerboard (Zero Moiré Shimmer at Any Distance!)
float filteredChecker(vec2 uv, vec2 fw) {
    vec2 w = max(fw, vec2(1e-4));
    vec2 i = (floor(uv) + clamp(fract(uv) / w, 0.0, 1.0))
           - (floor(uv - 0.5) + clamp(fract(uv - 0.5) / w, 0.0, 1.0));
    // Smoothly attenuate high-frequency pattern to 0.5 as pixel footprint approaches 1 tile
    vec2 atten = clamp(1.0 - w * 1.35, 0.0, 1.0);
    float p = (i.x * 2.0 - 1.0) * (i.y * 2.0 - 1.0) * atten.x * atten.y;
    return 0.5 + 0.5 * p;
}

// Evaluates the Infinite Polished Cream-Marble, Honey-Oak & Gold Inlay Floor with Planar Reflections!
vec3 evalModernInfiniteFloor(vec3 fPos, vec3 rd, float hitDist) {
    vec2 tileUv = fPos.xz * 0.25; // 2m x 2m tiles -> period 4m
    // Analytic screen/distance filter width prevents distant horizon shimmer completely!
    vec2 fw = vec2(max(0.012, hitDist * 0.0038 / max(0.14, abs(rd.y))));
    float chk = filteredChecker(tileUv * 2.0, fw * 2.0);

    // Subtle warm Calacatta gold-grey marble veining
    float vein = pow(0.5 + 0.5 * sin(fPos.x * 0.85 + sin(fPos.z * 0.65) * 1.8), 6.0)
               * (1.0 - smoothstep(25.0, 95.0, hitDist));
    vec3 ivoryTile = vec3(0.985, 0.965, 0.920) - vec3(0.04, 0.05, 0.07) * vein;
    vec3 creamTile = vec3(0.935, 0.890, 0.805) + vec3(0.04, 0.03, 0.01) * vein;
    vec3 marble = mix(creamTile, ivoryTile, chk);

    // Anti-aliased champagne-gold micro-grout lines (fades out cleanly with distance)
    vec2 gUv = fract(fPos.xz * 0.5);
    float gDist = min(min(gUv.x, 1.0 - gUv.x), min(gUv.y, 1.0 - gUv.y));
    float groutFade = 1.0 - smoothstep(18.0, 65.0, hitDist);
    float groutMask = (1.0 - smoothstep(0.012, 0.038, gDist)) * groutFade;
    marble = mix(marble, vec3(0.90, 0.75, 0.42), groutMask * 0.48);

    // Grand Cross-Aisles of Warm Honey-Oak Parquet + Brushed Champagne-Gold Trim
    float minAxis = min(abs(fPos.x), abs(fPos.z));
    float parquet = smoothstep(2.65, 2.35, minAxis);
    float goldTrim = smoothstep(0.24, 0.05, abs(minAxis - 2.45));
    vec3 oakCol = vec3(0.87, 0.71, 0.48);
    vec3 baseCol = mix(marble, oakCol, parquet * 0.85);
    baseCol = mix(baseCol, vec3(0.97, 0.82, 0.40), goldTrim * 0.90);

    // Warm 3000K sunlit chandelier light pools in every 16m bay
    vec2 cell = fPos.xz - BAY_SIZE * floor((fPos.xz + HALF_BAY) / BAY_SIZE);
    float lightPool = exp(-length(cell) * 0.18);
    vec3 lit = baseCol * (0.78 + 0.26 * lightPool) + vec3(1.0, 0.92, 0.68) * lightPool * 0.08;

    // Smooth Planar Mirror Reflection of overhead Coffered Skylights & Gold Chandeliers
    float reflT = CEIL_Y / max(0.06, abs(rd.y));
    vec3 reflCeilPos = vec3(fPos.x, 0.0, fPos.z) + vec3(rd.x, abs(rd.y), rd.z) * min(reflT, 110.0);
    vec3 reflCol = evalModernCofferedCeiling(reflCeilPos);
    float fresnel = pow(1.0 - clamp(abs(rd.y), 0.0, 1.0), 3.0);
    float reflectivity = (1.0 - parquet * 0.55) * (0.14 + 0.30 * fresnel);
    return mix(lit, reflCol, reflectivity);
}

// Full 3D World-Space Raymarcher for the 320m Warm Ivory-Gold Modern Restaurant Dimension!
vec4 raymarchModernRestaurant(vec3 camDom, vec3 rd, float time) {
    float tFloor = (rd.y < -0.001 && camDom.y > 0.0) ? (-camDom.y / rd.y) : 1e5;
    float tCeil  = (rd.y >  0.001 && camDom.y < CEIL_Y) ? ((CEIL_Y - camDom.y) / rd.y) : 1e5;
    float tPlane = min(tFloor, tCeil);
    float tMax   = min(MAX_RAY_DIST, tPlane);

    float t = 0.40;
    float hitMat = 0.0;
    for (int i = 0; i < 48; i++) {
        if (t >= tMax) break;
        vec3 p = camDom + rd * t;
        if (p.y < -0.5 || p.y > CEIL_Y + 0.5) break;
        vec2 h = mapModernRestaurantHall(p);
        if (h.x < 0.032) {
            hitMat = h.y;
            break;
        }
        t += max(0.16, h.x * 0.84);
    }

    vec3 col;
    vec3 hitP;
    float finalT;

    if (hitMat > 0.5 && t < tMax) {
        finalT = t;
        hitP = camDom + rd * t;
        vec3 n = calcHallNormal(hitP);
        vec3 sunDir = normalize(vec3(-0.32, 0.82, -0.46));
        float diff = 0.62 + 0.38 * max(dot(n, sunDir), 0.0);
        float upBounce = 0.5 + 0.5 * n.y;
        float rim = pow(1.0 - clamp(abs(dot(n, -rd)), 0.0, 1.0), 2.6);
        vec2 cell = hitP.xz - BAY_SIZE * floor((hitP.xz + HALF_BAY) / BAY_SIZE);

        if (hitMat < 1.5) {
            // Material 1: Fluted Warm Cream-Marble Column + Brushed Champagne-Gold Collars & 3000K Cove Strip
            bool goldTrim = (hitP.y < 1.32) || (hitP.y > 15.05) || (abs(hitP.y - 7.8) < 0.26);
            float flute = 0.96 + 0.04 * cos(atan(n.z, n.x) * 16.0);
            float coveStrip = smoothstep(0.18, 0.04, min(abs(abs(cell.x) - 6.85), abs(abs(cell.y) - 6.85)))
                            * smoothstep(1.5, 1.8, hitP.y) * smoothstep(14.8, 14.5, hitP.y);
            vec3 baseMat = goldTrim ? vec3(0.95, 0.79, 0.37) : (vec3(0.985, 0.960, 0.905) * flute);
            col = baseMat * (diff * 0.90 + upBounce * 0.14)
                + vec3(1.00, 0.91, 0.58) * coveStrip * 0.75
                + vec3(1.00, 0.92, 0.68) * rim * 0.28;
        } else if (hitMat < 2.5) {
            // Material 2: Sunlit Warm Cream-White Vault Arch + Frosted Golden Clerestory Glow
            float clerestory = smoothstep(15.2, 18.8, hitP.y);
            col = vec3(0.98, 0.95, 0.88) * diff
                + vec3(1.00, 0.91, 0.64) * clerestory * 0.45
                + vec3(0.96, 0.80, 0.38) * rim * 0.35;
        } else if (hitMat < 3.5) {
            // Material 3: Modern Cream-Leather & Honey-Oak Dining Booths + White Marble Tabletops
            bool tabletop = (hitP.y > 0.86 && hitP.y < 0.95 && abs(cell.y) < 0.78);
            bool woodBase = (hitP.y < 0.36);
            if (tabletop) {
                col = vec3(0.99, 0.98, 0.95) * (diff + 0.12);
            } else if (woodBase) {
                col = vec3(0.84, 0.66, 0.43) * diff;
            } else {
                col = vec3(0.98, 0.94, 0.86) * diff
                    + vec3(0.95, 0.79, 0.36) * smoothstep(1.22, 1.30, hitP.y) * 0.45;
            }
        } else {
            // Material 4: Warm 3000K Crystal-Gold Chandelier Rings & Designer Mushroom Lamps
            col = mix(vec3(1.00, 0.90, 0.54), vec3(1.00, 0.97, 0.84), 0.5 + 0.3 * sin(hitP.y * 1.2 + time * 1.5)) * 1.18;
        }
    } else if (tPlane < MAX_RAY_DIST) {
        finalT = tPlane;
        hitP = camDom + rd * finalT;
        if (tFloor < tCeil) {
            col = evalModernInfiniteFloor(hitP, rd, finalT);
        } else {
            col = evalModernCofferedCeiling(hitP);
        }
    } else {
        finalT = MAX_RAY_DIST;
        hitP = camDom + rd * MAX_RAY_DIST;
        col = vec3(0.98, 0.94, 0.85);
    }

    // Gentle Sunlit Golden Architectural God-Rays slanting across the Grand Banquet Hall
    vec3 midP = camDom + rd * min(finalT * 0.50, 52.0);
    float sunBeam = pow(0.5 + 0.5 * sin(midP.x * 0.16 + midP.y * 0.12 - time * 0.35) * cos(midP.z * 0.16 + time * 0.25), 3.0);
    float beamMask = smoothstep(8.0, 46.0, finalT) * (1.0 - smoothstep(150.0, 290.0, finalT));
    col += vec3(1.00, 0.92, 0.66) * sunBeam * beamMask * 0.14;

    // Smooth Atmospheric Perspective into Warm Sunlit Champagne-Cream Horizon
    float fogFactor = 1.0 - exp(-finalT * 0.0082);
    vec3 warmHorizon = mix(vec3(0.99, 0.955, 0.875), vec3(1.00, 0.93, 0.78), clamp(1.0 - abs(rd.y) * 2.4, 0.0, 1.0));
    col = mix(col, warmHorizon, fogFactor);

    return vec4(col, length(hitP));
}

vec3 extractSoftHighlight(vec3 c) {
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float knee = smoothstep(0.76, 1.02, luma);
    return c * knee * knee;
}

void main() {
    vec2 texel = 1.0 / max(vec2(1.0), OutSize);
    vec2 aspect = vec2(OutSize.x / max(1.0, OutSize.y), 1.0);
    vec2 focus = vec2(FocusUvX, FocusUvY);
    vec2 delta = (texCoord - focus) * aspect;
    float rDist = length(delta);
    vec2 radialDir = delta / max(1e-4, rDist);

    vec2 sampleUv = texCoord;

    if (ImpactFrame < -0.005) {
        float pull = -ImpactFrame * exp(-rDist * 2.2) * 0.022;
        sampleUv = clamp(sampleUv - (texCoord - focus) * pull, 0.001, 0.999);
    }
    if (ShockStrength > 0.002) {
        float ringDelta = rDist - ShockRing;
        float shell = exp(-ringDelta * ringDelta * 155.0) * (ringDelta > 0.0 ? 1.0 : -0.78);
        vec2 warp = (radialDir / aspect) * shell * ShockStrength * 0.024;
        sampleUv = clamp(sampleUv + warp, 0.001, 0.999);
    }

    vec3 mcColor;
    float dispAmt = ShockStrength * 0.0036 * exp(-pow((rDist - ShockRing) * 9.0, 2.0))
                  + ChromaticBoost * 0.0040 * smoothstep(0.08, 0.88, rDist);
    if (dispAmt > 0.00025) {
        vec2 dUv = (radialDir / aspect) * dispAmt;
        mcColor.r = texture(DiffuseSampler, clamp(sampleUv + dUv, 0.001, 0.999)).r;
        mcColor.g = texture(DiffuseSampler, sampleUv).g;
        mcColor.b = texture(DiffuseSampler, clamp(sampleUv - dUv, 0.001, 0.999)).b;
    } else {
        mcColor = texture(DiffuseSampler, sampleUv).rgb;
    }

    vec3 color = mcColor;

    // TRUE 320m FULL-WORLD WARM IVORY-GOLD MODERN REALITY-MARBLE ENGINE
    if (DomainStrength > 0.005 && ViewerInside > 0.5) {
        mat4 invProjView = mat4(Inv0, Inv1, Inv2, Inv3, Inv4, Inv5, Inv6, Inv7,
                                Inv8, Inv9, Inv10, Inv11, Inv12, Inv13, Inv14, Inv15);
        vec2 ndc = sampleUv * 2.0 - 1.0;
        vec4 nearH = invProjView * vec4(ndc, -1.0, 1.0);
        vec4 farH  = invProjView * vec4(ndc,  1.0, 1.0);
        vec3 ro = nearH.xyz / max(1e-5, nearH.w);
        vec3 farP = farH.xyz / max(1e-5, farH.w);
        vec3 rd = normalize(farP - ro);

        vec3 domCenterRel = vec3(FieldCenterX, FieldCenterY, FieldCenterZ);
        vec3 camDomPos = ro - domCenterRel;

        float depth = texture(DepthSampler, sampleUv).r;
        bool isSky = (depth >= 0.99995);
        vec3 mcDomPos = camDomPos + rd * MAX_RAY_DIST;
        vec3 worldN = vec3(0.0, 1.0, 0.0);
        bool smoothSurface = false;

        if (!isSky) {
            vec4 hitH = invProjView * vec4(ndc, depth * 2.0 - 1.0, 1.0);
            vec3 mcRelPos = hitH.xyz / max(1e-5, hitH.w);
            mcDomPos = mcRelPos - domCenterRel;
            vec3 dpx = dFdx(mcRelPos);
            vec3 dpy = dFdy(mcRelPos);
            // Depth-discontinuity guard eliminates single-pixel edge reflection flashes!
            if (max(length(dpx), length(dpy)) < 0.35) {
                vec3 rawN = cross(dpx, dpy);
                float nLen = length(rawN);
                if (nLen > 1e-6) {
                    worldN = rawN / nLen;
                    smoothSurface = true;
                }
            }
        }

        float mcHorizR = length(mcDomPos.xz);
        bool isForegroundStage = (!isSky) && (mcHorizR < StageRadius) && (mcDomPos.y > -2.5) && (mcDomPos.y < 16.2);

        vec3 domainPixel;
        float pixelDomDist;

        if (isForegroundStage) {
            pixelDomDist = length(mcDomPos);
            // Warm 3000K architectural color harmony on foreground entities & blocks
            domainPixel = mcColor * vec3(1.03, 1.00, 0.95);

            // Stable planar ceiling reflection on horizontal Smooth Quartz / Parquet stage floor
            if (smoothSurface && abs(worldN.y) > 0.78 && abs(mcDomPos.y) < 0.60) {
                float reflT = CEIL_Y / max(0.08, abs(rd.y));
                vec3 reflPos = vec3(mcDomPos.x, 0.0, mcDomPos.z) + vec3(rd.x, abs(rd.y), rd.z) * min(reflT, 95.0);
                vec3 reflCol = evalModernCofferedCeiling(reflPos);
                float fresnel = pow(1.0 - clamp(abs(rd.y), 0.0, 1.0), 2.8);
                domainPixel = mix(domainPixel, reflCol, (0.10 + 0.22 * fresnel) * DomainStrength);
            }
        } else {
            vec4 rayRes = raymarchModernRestaurant(camDomPos, rd, PulseTime);
            domainPixel = rayRes.rgb;
            pixelDomDist = rayRes.w;
        }

        float waveMask = (1.0 - smoothstep(DomainRadius - 3.6, DomainRadius + 1.2, pixelDomDist)) * DomainStrength;

        // Blazing Champagne-Gold & Ivory Reality-Wave Curtain at advancing/receding boundary
        float waveFront = (1.0 - smoothstep(0.0, 4.2, abs(pixelDomDist - DomainRadius))) * DomainStrength;
        if (DomainRadius < 295.0 && waveFront > 0.01) {
            vec3 goldFire = mix(vec3(1.00, 0.82, 0.34), vec3(1.00, 0.97, 0.85), 0.5 + 0.5 * sin(PulseTime * 10.0 + rd.x * 14.0));
            domainPixel += goldFire * waveFront * 1.15;
        }

        if (ShatterProgress > 0.01) {
            vec2 glassUv = vec2(atan(rd.z, rd.x) * 3.8, rd.y * 7.5);
            vec2 vg = voronoiGlass(glassUv);
            float crackEdge = 1.0 - smoothstep(0.02, 0.09 + ShatterProgress * 0.08, vg.x);
            float shardBroken = smoothstep(vg.y * 0.82, vg.y * 0.82 + 0.18, ShatterProgress);
            waveMask *= (1.0 - shardBroken);
            vec3 crackGlow = mix(vec3(1.00, 0.82, 0.36), vec3(1.00, 0.98, 0.90), crackEdge);
            color = mix(mcColor, domainPixel, waveMask) + crackGlow * crackEdge * (1.0 - shardBroken * 0.5) * 1.15;
        } else {
            color = mix(mcColor, domainPixel, waveMask);
        }
    }

    // Gentle Luxury Architectural Soft-Knee Highlight Bloom
    if (BloomStrength > 0.006) {
        vec3 bloomAcc = vec3(0.0);
        float wSum = 0.0;
        const float GOLDEN_ANG = 2.39996323;
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            float radPx = 2.0 + fi * 1.5;
            float ang = fi * GOLDEN_ANG;
            vec2 off = vec2(cos(ang), sin(ang)) * texel * radPx;
            float w = exp(-fi * 0.14);
            vec3 sCol = texture(DiffuseSampler, clamp(sampleUv + off, 0.001, 0.999)).rgb;
            bloomAcc += extractSoftHighlight(sCol) * w;
            wSum += w;
        }
        vec3 selfGlow = extractSoftHighlight(color) * 0.45;
        vec3 softBloom = ((bloomAcc / max(1e-4, wSum)) * 0.42 + selfGlow) * BloomStrength;
        color = 1.0 - (1.0 - clamp(color, 0.0, 1.0)) * (1.0 - clamp(softBloom * 0.65, 0.0, 0.48));
    }

    // Warm Amber-Crimson Countdown Vignette in Final 3 Seconds
    if (UrgencyPulse > 0.005) {
        float vig = smoothstep(0.35, 0.96, rDist) * UrgencyPulse;
        color = mix(color, vec3(0.92, 0.24, 0.18), vig * 0.32);
    }

    // Beat 1 Warm Sepia-Ivory Stasis -> Beat 2 Gold-Leaf Manga Impact Frame!
    if (ImpactFrame < -0.005) {
        float mono = clamp(-ImpactFrame, 0.0, 1.0);
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        vec3 warmSepia = vec3(luma * 1.04, luma * 0.97, luma * 0.84);
        color = mix(color, warmSepia, mono * 0.90);
        float edgeDark = smoothstep(0.18, 0.92, rDist) * mono;
        color *= (1.0 - edgeDark * 0.42);
    } else if (ImpactFrame > 0.008) {
        float imp = clamp(ImpactFrame, 0.0, 1.0);
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float ang = atan(delta.y, delta.x);
        float rays = pow(0.5 + 0.5 * sin(ang * 48.0 + sin(ang * 8.0) * 3.0), 10.0) * smoothstep(0.06, 0.56, rDist);
        float inkMask = smoothstep(0.32, 0.52, luma + rays * 0.46);
        vec3 darkEspresso = vec3(0.10, 0.06, 0.03);
        vec3 goldIvoryPaper = mix(vec3(1.0, 0.99, 0.95), vec3(1.0, 0.85, 0.38), rays * 0.60);
        vec3 mangaFrame = mix(darkEspresso, goldIvoryPaper, inkMask);
        color = mix(color, mangaFrame, imp * 0.84);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
