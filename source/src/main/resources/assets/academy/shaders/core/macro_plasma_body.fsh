#version 150

uniform int ballCount;
uniform vec4 ball0;
uniform vec4 ball1;
uniform vec4 ball2;
uniform vec4 ball3;
uniform vec4 ball4;
uniform vec4 ball5;
uniform vec4 ball6;
uniform vec4 ball7;
uniform vec4 ball8;
uniform vec4 ball9;
uniform vec4 ball10;
uniform vec4 ball11;
uniform vec4 ball12;
uniform vec4 ball13;
uniform vec4 ball14;
uniform vec4 ball15;
uniform float alpha;
uniform float detailTime;

in vec3 camspace;
out vec4 fragColor;

float f(vec3 position) {
    vec4 balls[16] = vec4[16](ball0, ball1, ball2, ball3, ball4, ball5, ball6, ball7, ball8, ball9, ball10, ball11, ball12, ball13, ball14, ball15);
    float ret = 0.0;
    for (int i = 0; i < ballCount; ++i) {
        float distance = max(0.1, length(position - balls[i].xyz));

        ret += alpha * balls[i].w / (distance * distance);
    }
    return clamp(ret, 0, 2);
}

vec4 rayMarch(vec3 begin, vec3 dir) {
    dir *= 0.09375;

    vec3 pos = begin;

    vec4 accum = vec4(0, 0, 0, 0);
    for (int i = 0; i < 32 && accum.a < 1; ++i) {
        float density = f(pos);
        float filaments = sin(pos.x*16.0+sin(pos.y*11.0+detailTime))*sin(pos.z*13.0-detailTime);
        density *= .88 + .18*filaments;

        float alpha = 0.046875 * density;
        vec3 crl = mix(vec3(0.43, 0.74, 1), vec3(0.98, 0.51, 0.92), 1-density/2);

        accum.rgb = mix(accum.rgb, crl, alpha / max(0.00001, accum.a + alpha));
        accum.a += alpha;

        pos += dir;
    }

    if (accum.a < 0.2) { // Make alpha in [0.2, 0.1] map to [0.2, 0], to make blending look normal in edge area
        accum.a = 2 * accum.a - 0.2;
    }
    // accum.a = clamp(accum.a, 0, 1); //* 0.8;
    return accum;
}

void main() {
    vec3 cam = camspace;
    cam.z = -cam.z;

    vec3 dir = normalize(cam);

    vec4 rc = rayMarch(cam - dir * 3, dir);
    rc.a = clamp(rc.a, 0, 1) * (0.5 + alpha * 0.5);

	fragColor = rc;
}
