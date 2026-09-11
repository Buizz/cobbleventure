package dev.buizz.cobbleventure.bootstrap.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

final class CobblenavSpawnBucketTest {
    @Test
    void readsCobblemon18StringBucket() throws ReflectiveOperationException {
        assertEquals("common", detailBucket(new StringDetail()));
    }

    @Test
    void retainsObjectBucketCompatibility() throws ReflectiveOperationException {
        assertEquals("rare", detailBucket(new ObjectDetail()));
    }

    @Test
    void keepsMixinHelperPrivate() throws ReflectiveOperationException {
        Method method = detailBucketMethod();
        assertTrue(Modifier.isPrivate(method.getModifiers()));
    }

    private static String detailBucket(Object detail) throws ReflectiveOperationException {
        Method method = detailBucketMethod();
        method.setAccessible(true);
        return (String) method.invoke(null, detail);
    }

    private static Method detailBucketMethod() throws NoSuchMethodException {
        return CobblenavSpawnDataHelperMixin.class.getDeclaredMethod("detailBucket", Object.class);
    }

    public static final class StringDetail {
        public String getBucket() { return "common"; }
    }

    public static final class ObjectDetail {
        public Bucket getBucket() { return new Bucket(); }
    }

    public static final class Bucket {
        public String getName() { return "rare"; }
    }
}
