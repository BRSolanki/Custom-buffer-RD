$input a_color0, a_position, a_texcoord0, a_texcoord1
#ifdef INSTANCING
    $input i_data1, i_data2, i_data3
#endif
$output v_color0, v_fog, v_texcoord0, v_lightmapUV, v_ditheringAndMaskTinting, v_worldPos

#include <bgfx_shader.sh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 RenderChunkFogAlpha;
uniform vec4 ViewPositionAndTime;
#ifndef DEPTH_ONLY_OPAQUE
uniform vec4 MeshContext;
uniform vec4 SubPixelOffset;
#endif

void main() {
  #ifdef INSTANCING
    mat4 model = mtxFromCols(i_data1, i_data2, i_data3, vec4(0.0, 0.0, 0.0, 1.0));
  #else
    mat4 model = u_model[0];
  #endif
  vec3 worldPos = mul(model, vec4(a_position, 1.0)).xyz;

  vec4 color = a_color0;
  #ifdef RENDER_AS_BILLBOARDS
    worldPos += vec3(0.5, 0.5, 0.5);
    vec3 viewDir = normalize(worldPos - ViewPositionAndTime.xyz);
    vec3 boardPlane = normalize(cross(vec3(0.0, 1.0, 0.0), viewDir));
    worldPos -= (cross(viewDir, boardPlane) * (a_color0.z - 0.5)) + (boardPlane * (a_color0.x - 0.5));
    color = vec4_splat(1.0);
  #endif

  vec3 modelCamPos = ViewPositionAndTime.xyz - worldPos;
  float camDis = length(modelCamPos);

  // MeshContext.x indicates underwater context
  vec4 fogControl = FogAndDistanceControl;
  #ifndef DEPTH_ONLY_OPAQUE
  if (MeshContext.x > 0.5) {
    fogControl = vec4(0.99, 1.0, 100000.0, 100000.0);
  }
  #endif

  float fogIntensity = clamp((((camDis / fogControl.z) + RenderChunkFogAlpha.x) - fogControl.x) / (fogControl.y - fogControl.x), 0.0, 1.0);
  vec4 fog = vec4(FogColor.rgb, fogIntensity);

  #ifdef TRANSPARENT
    if (a_color0.a < 0.95) {
      color.a = mix(a_color0.a, 1.0, clamp(camDis / fogControl.w, 0.0, 1.0));
    }
  #endif

  // Exact vanilla 1.20+ RenderChunk texture coordinates & lightmap unpacking
  uvec2 tc0_u = uvec2(round(a_texcoord0 * 65535.0));
  vec2 uv0 = vec2(float((tc0_u.x & 32767u) << uint(1)), float((tc0_u.y & 32767u) << uint(1))) * vec2(1.525902189314365386962890625e-05);
  uv0.x += (3.0517578125e-05 * ((2.0 * float((tc0_u.x & 32768u) >> uint(15))) - 1.0));
  uv0.y += (3.0517578125e-05 * ((2.0 * float((tc0_u.y & 32768u) >> uint(15))) - 1.0));

  uvec2 tc1_u = uvec2(round(a_texcoord1 * 65535.0));
  vec2 ditheringAndMaskTinting = vec2(notEqual((tc1_u & uvec2(256u)), uvec2(0u)));
  vec2 uv1 = vec2(uvec2(tc1_u.y >> 4u, tc1_u.y) & uvec2(15u)) * vec2(0.066666670143604278564453125);

  v_texcoord0 = uv0;
  v_lightmapUV = uv1;
  v_color0 = color;
  v_fog = fog;
  v_worldPos = worldPos;
  v_ditheringAndMaskTinting = ditheringAndMaskTinting;

  #ifndef DEPTH_ONLY_OPAQUE
    mat4 offsetProj = u_proj;
    #if BGFX_SHADER_LANGUAGE_GLSL
      offsetProj[2][0] += SubPixelOffset.x;
      offsetProj[2][1] -= SubPixelOffset.y;
    #else
      offsetProj[0][2] += SubPixelOffset.x;
      offsetProj[1][2] -= SubPixelOffset.y;
    #endif
    vec4 position = mul(offsetProj, mul(u_view, vec4(worldPos, 1.0)));
  #else
    vec4 position = mul(u_viewProj, vec4(worldPos, 1.0));
  #endif

  gl_Position = position;
}
