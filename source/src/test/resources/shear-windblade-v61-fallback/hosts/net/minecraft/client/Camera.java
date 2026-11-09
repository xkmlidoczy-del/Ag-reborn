package net.minecraft.client;
import net.minecraft.world.phys.Vec3;import org.joml.*;
public record Camera(Vec3 getPosition,Vector3f getLookVector,Vector3f getLeftVector){public Matrix4f rotation(){return new Matrix4f();}}
