package net.aether.spigot.knockback;

/**
 * Formules de knockback utilisées en practice HCF.
 * SIMPLE reprend le modèle friction / extra sprint.
 * ADVANCED reprend les multiplicateurs au sol, en sprint et en l'air.
 */
public final class KnockbackMath {

    public static final class Vec {
        public final double x;
        public final double y;
        public final double z;

        public Vec(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Input {
        public double motX;
        public double motY;
        public double motZ;
        public double dirX;
        public double dirZ;
        public boolean onGround;
        public boolean sprinting;
        public boolean backHit;
        public int knockbackLevel;
        public int comboTicks;
    }

    private KnockbackMath() {
    }

    public static Vec apply(KnockbackProfile profile, Input input) {
        double dx = input.dirX;
        double dz = input.dirZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6D) {
            dx = 1.0D;
            dz = 0.0D;
        } else {
            dx /= length;
            dz /= length;
        }
        if ("SIMPLE".equalsIgnoreCase(profile.type)) {
            return simple(profile, input, dx, dz);
        }
        return advanced(profile, input, dx, dz);
    }

    public static Vec rod(KnockbackProfile profile, double dirX, double dirZ) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-6D) {
            dirX = 1.0D;
            dirZ = 0.0D;
        } else {
            dirX /= length;
            dirZ /= length;
        }
        double speed = profile.rodSpeed;
        return new Vec(dirX * profile.rodHorizontal * speed, profile.rodVertical * speed, dirZ * profile.rodHorizontal * speed);
    }

    public static Vec bow(KnockbackProfile profile, double dirX, double dirY, double dirZ, int punchLevel) {
        double horizontal = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (horizontal < 1.0E-6D) {
            dirX = 1.0D;
            dirZ = 0.0D;
        } else {
            dirX /= horizontal;
            dirZ /= horizontal;
        }
        double scale = profile.bowHorizontal;
        if (punchLevel > 0) {
            scale *= (punchLevel + 1) * profile.bowPunchMultiplier;
        }
        double y = profile.bowVertical;
        if (dirY > 0.15D && profile.bowBoostDirectional) {
            y += dirY * 0.15D;
        }
        return new Vec(dirX * scale, y, dirZ * scale);
    }

    private static Vec simple(KnockbackProfile profile, Input input, double dx, double dz) {
        double horizontal = profile.horizontal;
        double vertical = profile.vertical;
        if (input.sprinting) {
            horizontal += profile.extraHorizontal;
            vertical += profile.extraVertical;
        }
        if (profile.enableOnePointSeven && input.backHit) {
            horizontal *= profile.onePointSevenMultiplier;
        }
        if (input.knockbackLevel > 0) {
            horizontal *= (input.knockbackLevel + 1) - profile.horizontalKnockbackDeduction;
        }
        if (vertical > profile.verticalLimit) {
            vertical = profile.verticalLimit;
        }
        double friction = profile.friction <= 0.0001D ? 1.0D : profile.friction;
        double mx = input.motX / friction * profile.slowdown;
        double mz = input.motZ / friction * profile.slowdown;
        return new Vec(mx + dx * horizontal, limitCombo(profile, input, vertical), mz + dz * horizontal);
    }

    private static Vec advanced(KnockbackProfile profile, Input input, double dx, double dz) {
        double horizontal = profile.horizontal;
        if (input.onGround) {
            horizontal *= profile.horizontalOnGround;
        }
        if (input.sprinting) {
            horizontal *= profile.horizontalSprinting;
        }
        if (input.knockbackLevel > 0) {
            horizontal *= (input.knockbackLevel + 1) - profile.horizontalKnockbackDeduction;
        }
        if (profile.enableOnePointSeven && input.backHit) {
            horizontal = profile.onePointSevenHorizontal;
        }
        double mx = 0.0D;
        double mz = 0.0D;
        if (profile.horizontalInherit) {
            mx = input.motX * profile.horizontalFriction * profile.slowdown;
            mz = input.motZ * profile.horizontalFriction * profile.slowdown;
        }
        double vertical = profile.vertical;
        if (input.onGround) {
            vertical *= profile.verticalOnGround;
        } else {
            vertical *= profile.verticalInAir;
        }
        if (input.sprinting) {
            vertical *= profile.verticalSprinting;
        }
        double y;
        if (profile.verticalInherit) {
            y = input.motY * profile.verticalFriction + vertical;
        } else {
            y = vertical;
        }
        if (profile.enableVerticalLimit && y > profile.verticalLimit) {
            y = 0.0D;
        }
        return new Vec(mx + dx * horizontal, limitCombo(profile, input, y), mz + dz * horizontal);
    }

    private static double limitCombo(KnockbackProfile profile, Input input, double vertical) {
        if (!profile.comboMode) {
            return vertical;
        }
        if (input.comboTicks >= profile.comboTicks || vertical > profile.comboHeight) {
            return profile.comboVelocity;
        }
        return vertical;
    }
}
