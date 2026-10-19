$input v_color0, v_fog, v_texcoord0, v_lightmapUV, v_ditheringAndMaskTinting, v_worldPos

#include <bgfx_shader.sh>

#if !defined(DEPTH_ONLY_OPAQUE)
  SAMPLER2D_AUTOREG(s_MatTexture);

  #if !defined(DEPTH_ONLY)
    SAMPLER2D_AUTOREG(s_LightMapTexture);

    #if defined(SEASONS) && (defined(ALPHA_TEST) || defined(OPAQUE))
      SAMPLER2D_AUTOREG(s_SeasonsTexture);
    #endif

    #ifdef TRANSPARENT
      SAMPLER2D_AUTOREG(s_ScreenBuffer);
      SAMPLER2D_AUTOREG(s_SceneDepthBuffer);
    #endif
  #endif
#endif

#if !defined(DEPTH_ONLY_OPAQUE) && !defined(DEPTH_ONLY)
  uniform vec4 FogColor;
  #if defined(TRANSPARENT)
    uniform vec4 ViewPositionAndTime;
  #endif
#endif

// ==========================================
// CustomBuffer Configuration & Subpack Toggles
// ==========================================
#if !defined(NO_REFLECTIONS)
  #define SCREEN_BUFFER_DEMO
#endif

#if !defined(NO_FOAM)
  #define SCENE_DEPTH_FOAM
#endif

// Reflections configuration
#define SCREEN_BUFFER_STRENGTH 0.85
#define SCREEN_BUFFER_RIPPLE 0.10     // Ripple strength
#define SCREEN_BUFFER_BLUR 0.003      // Soft reflection edges
#define SCREEN_BUFFER_TINT 0.4        // Water color blend in reflection
#define SCREEN_BUFFER_EDGE_FADE 0.3   // Edge vignette fade distance
#define SCREEN_BUFFER_REFLECT_DIST 24.0
#define SCREEN_BUFFER_FRESNEL         // Stronger reflection at grazing angles
//#define SCREEN_BUFFER_FLIP_Y        // Uncomment if upside down

// Shoreline foam configuration
#define SCENE_DEPTH_NEAR 0.05         // Camera near plane
#define SCENE_DEPTH_FAR 512.0         // Camera far plane
#define SCENE_DEPTH_FOAM_WIDTH 0.9    // Foam band width in blocks
#define SCENE_DEPTH_FOAM_SPEED 0.6    // Foam animation speed
#define SCENE_DEPTH_FOAM_SCALE 2.5    // Foam pattern scale
//#define SCENE_DEPTH_REVERSED        // Uncomment if depth is reversed

#if defined(TRANSPARENT)
// Noise functions for water ripples and shoreline foam
float foamHash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float foamNoise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(foamHash(i), foamHash(i + vec2(1.0, 0.0)), f.x),
             mix(foamHash(i + vec2(0.0, 1.0)), foamHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Convert depth buffer value (0..1) to linear view distance (blocks)
float sceneDepthLinear(float d) {
  #ifdef SCENE_DEPTH_REVERSED
    d = 1.0 - d;
  #endif
  float ndc = d * 2.0 - 1.0;
  return (2.0 * SCENE_DEPTH_NEAR * SCENE_DEPTH_FAR) / (SCENE_DEPTH_FAR + SCENE_DEPTH_NEAR - ndc * (SCENE_DEPTH_FAR - SCENE_DEPTH_NEAR));
}
#endif

void main() {
  #if defined(DEPTH_ONLY_OPAQUE)
    gl_FragColor = vec4_splat(0.0);
  #elif defined(DEPTH_ONLY)
    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
    if (diffuse.a < 0.5) {
      discard;
    }
    gl_FragColor = vec4_splat(0.0);
  #else
    // Sample terrain material texture
    vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);

    #ifdef ALPHA_TEST
      if (diffuse.a < 0.5) {
        discard;
      }
    #endif

    // Foliage / Biome / Mask Tinting
    #if defined(SEASONS) && (defined(ALPHA_TEST) || defined(OPAQUE))
      diffuse.rgb *= mix(vec3_splat(1.0), 2.0 * texture2D(s_SeasonsTexture, v_color0.xy).rgb, v_color0.z);
      diffuse.rgb *= v_color0.w;
      diffuse.a = 1.0;
    #else
      if (v_ditheringAndMaskTinting.y > 0.5) {
        vec3 tinted = mix(diffuse.rgb, diffuse.rgb * v_color0.rgb, diffuse.a) * v_color0.a;
        diffuse = vec4(tinted, 1.0);
      } else {
        diffuse.rgb *= v_color0.rgb;
        diffuse.a *= v_color0.a;
      }
    #endif

    // Lightmap lighting
    diffuse.rgb *= texture2D(s_LightMapTexture, v_lightmapUV).rgb;

    // Distance fog
    diffuse.rgb = mix(diffuse.rgb, FogColor.rgb, v_fog.a);

    // ==========================================
    // CustomBuffers Effects (Transparent Pass)
    // ==========================================
    #ifdef TRANSPARENT

      #ifdef SCREEN_BUFFER_DEMO
        // Screen-space water reflections on top faces
        vec3 viewDir = normalize(v_worldPos - ViewPositionAndTime.xyz);
        vec3 faceNormal = normalize(cross(dFdx(v_worldPos), dFdy(v_worldPos)));

        if (abs(faceNormal.y) > 0.9 && viewDir.y < 0.0) {
          float time = ViewPositionAndTime.w;
          vec2 rp = v_worldPos.xz * 1.7;
          vec2 rippleA = vec2(foamNoise(rp + vec2(time * 0.4, time * 0.3)), foamNoise(rp - vec2(time * 0.3, time * 0.5) + 7.3)) - 0.5;
          vec2 rippleB = vec2(foamNoise(rp * 2.9 - vec2(time * 0.7, time * 0.2) + 3.1), foamNoise(rp * 2.9 + vec2(time * 0.2, time * 0.6) + 11.7)) - 0.5;
          vec2 tilt = (rippleA + rippleB * 0.5) * SCREEN_BUFFER_RIPPLE;
          vec3 normal = normalize(vec3(tilt.x, 1.0, tilt.y));
          vec3 refl = reflect(viewDir, normal);
          refl.y = max(refl.y, 0.02);

          // Project reflected ray into screen space
          vec4 clip = mul(u_viewProj, vec4(v_worldPos + refl * SCREEN_BUFFER_REFLECT_DIST, 1.0));
          if (clip.w > 0.0) {
            vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
            #ifdef SCREEN_BUFFER_FLIP_Y
              uv.y = 1.0 - uv.y;
            #endif

            // Edge fade and mirrored boundary handling
            vec2 past = max(-uv, uv - 1.0);
            float over = max(past.x, past.y);
            vec2 m = clamp(1.0 - abs(1.0 - abs(uv)), 0.002, 0.998);
            float k = SCREEN_BUFFER_STRENGTH * (1.0 - smoothstep(0.0, SCREEN_BUFFER_EDGE_FADE, max(over, 0.0)));
            #ifdef SCREEN_BUFFER_FRESNEL
              k *= mix(0.25, 1.0, pow(1.0 - clamp(-viewDir.y, 0.0, 1.0), 3.0));
            #endif
            k *= 1.0 - v_fog.a;

            // 5-tap blur sampling for smooth reflections
            vec2 o = vec2(SCREEN_BUFFER_BLUR) + tilt * 0.05;
            vec3 reflected = texture2D(s_ScreenBuffer, m).rgb * 0.4
              + texture2D(s_ScreenBuffer, clamp(m + vec2(o.x, o.y), 0.002, 0.998)).rgb * 0.15
              + texture2D(s_ScreenBuffer, clamp(m + vec2(-o.x, o.y), 0.002, 0.998)).rgb * 0.15
              + texture2D(s_ScreenBuffer, clamp(m + vec2(o.x, -o.y), 0.002, 0.998)).rgb * 0.15
              + texture2D(s_ScreenBuffer, clamp(m + vec2(-o.x, -o.y), 0.002, 0.998)).rgb * 0.15;

            reflected *= mix(vec3_splat(1.0), clamp(diffuse.rgb * 2.0, 0.0, 1.0) * 0.7 + 0.3, SCREEN_BUFFER_TINT);
            diffuse.rgb = mix(diffuse.rgb, reflected, k);
          }
        }
      #endif

      #ifdef SCENE_DEPTH_FOAM
        // Water shoreline foam (detect water blocks by alpha < 0.95)
        if (v_color0.a < 0.95) {
          vec2 depthUV = gl_FragCoord.xy / vec2(textureSize(s_SceneDepthBuffer, 0));
          float behind = sceneDepthLinear(texture2D(s_SceneDepthBuffer, depthUV).r);
          float thickness = max(behind - sceneDepthLinear(gl_FragCoord.z), 0.0);

          float t = ViewPositionAndTime.w * SCENE_DEPTH_FOAM_SPEED;
          vec2 wp = v_worldPos.xz * SCENE_DEPTH_FOAM_SCALE;
          float n = foamNoise(wp + vec2(t, t * 0.7)) * 0.6 + foamNoise(wp * 2.1 - vec2(t * 0.8, t)) * 0.4;
          float shore = 1.0 - clamp(thickness / SCENE_DEPTH_FOAM_WIDTH, 0.0, 1.0);
          shore *= 0.8 + 0.2 * sin(t * 3.0 + n * 6.28);
          float foam = smoothstep(0.45, 0.65, n + shore * 0.6 - 0.3) * smoothstep(0.0, 0.15, shore);
          foam *= 1.0 - v_fog.a;

          diffuse.rgb = mix(diffuse.rgb, vec3(0.92, 0.96, 1.0) * texture2D(s_LightMapTexture, v_lightmapUV).rgb, foam);
          diffuse.a = max(diffuse.a, foam);
        }
      #endif

    #endif

    gl_FragColor = diffuse;
  #endif
}
