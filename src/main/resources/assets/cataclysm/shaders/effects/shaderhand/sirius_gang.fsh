#version 330

// Zenith ShaderHand prelude, ported to the 1.21.11 render-pipeline shader format.
//
// Zenith renders the hands into a dedicated framebuffer cleared to transparent black, then
// runs this shader with that framebuffer's colour+depth bound. Vanilla 1.21.11 clears the
// main depth texture right before renderItemInHand, so after the hand features are flushed
// the depth attachment is 1.0 everywhere except on hand pixels — the same exact silhouette
// Zenith gets from its own framebuffer, with no colour-delta guessing. ColorTexture is a
// copy of the scene, DepthTexture a copy of that depth, and handTexture() rebuilds the
// "hand FBO" alpha channel the original shader bodies expect.

in vec2 handUv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;

layout(std140) uniform ShaderHandData {
    vec2 resolution;
    vec2 handMotion;
    vec2 speed;
    vec2 iMouse;
    float time;
    float shift;
    float effectAlpha;
    float shaderHandPadding;
};

#define surfacePosition ((handUv - handMotion) * 2.0)

float handDepthMask(vec2 sampleUv) {
    if (sampleUv.x < 0.0 || sampleUv.y < 0.0 || sampleUv.x > 1.0 || sampleUv.y > 1.0) {
        return 0.0;
    }

    float depthValue = texture(DepthTexture, sampleUv).r;
    return smoothstep(0.999, 0.990, depthValue);
}

float sampleMask(vec2 sampleUv) {
    if (sampleUv.x < 0.0 || sampleUv.y < 0.0 || sampleUv.x > 1.0 || sampleUv.y > 1.0) {
        return 0.0;
    }

    return handDepthMask(sampleUv);
}

vec4 handTexture(vec2 sampleUv) {
    vec4 sampledColor = texture(ColorTexture, sampleUv);
    sampledColor.a = sampleMask(sampleUv);
    return sampledColor;
}

#define texture2D(sourceTexture, sampleUv) handTexture(sampleUv)

vec2 handEffectCoord(vec2 sampleUv) {
    return (sampleUv - handMotion + 0.5) * resolution.xy;
}

vec4 handFragCoord4() {
    return vec4(handEffectCoord(handUv), 0.0, 1.0);
}

// Converted from Sirius chams shader: gang.frag
float rand(vec2 n) {
 return fract(cos(dot(n, vec2(2.9898, 20.1414))) * 5.5453);
}

float noise(vec2 n) {
  const vec2 d = vec2(0.0, 1.0);
  vec2 b = floor(n), f = smoothstep(vec2(0.0), vec2(1.0), fract(n));
  return mix(mix(rand(b), rand(b + d.yx), f.x), mix(rand(b + d.xy), rand(b + d.yy), f.x), f.y);
}

float fbm(vec2 n){
   float total=0.,amplitude=1.5;
   for(int i=0;i<18;i++){
       total+=noise(n)*amplitude;
        n+=n;
        amplitude*=.45;
   }
  return total;
}

float calcLuma(vec3 color) {
    return 0.299*color.r + 0.587*color.g + 0.114*color.b;
}

void main(){
    const vec3 c1=vec3(0.502, 0.1059, 0.1059);
    const vec3 c2=vec3(167./255.,93./255.,110./255.);
    const vec3 c3=vec3(0.4902, 0.5333, 0.4902);
    const vec3 c4=vec3(0.2118, 0.3451, 0.2706);
    const vec3 c5=vec3(0.3176, 0.2549, 0.4);
    const vec3 c6=vec3(0.8, 0.3569, 0.3569);
    
    vec2 p=handFragCoord4().xy*5./resolution.xx;
    float q=fbm(p-time*.05);
    vec2 r=vec2(fbm(p+q+time*speed.x-p.x-p.y),fbm(p+q-time*speed.y));
    vec3 c=mix(c1,c2,fbm(p+r))+mix(c3,c4,r.x)-mix(c5,c6,r.y);
    float grad=handFragCoord4().y/resolution.y;

    vec4 centerCol = texture2D(texture, handUv);

    if (centerCol.a <= 0.001) {
        discard;
    }
    vec4 result;

    result = vec4(c*cos(shift*handFragCoord4().y/resolution.y),1.5);
    result.xyz*=1.15-grad;

    float alpha = calcLuma(result.rgb)*15;
    outColor = vec4(result.rgb, alpha);
    outColor.a *= effectAlpha;
}
