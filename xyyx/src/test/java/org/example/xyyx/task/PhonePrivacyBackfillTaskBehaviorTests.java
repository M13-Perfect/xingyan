package org.example.xyyx.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhonePrivacyBackfillTaskBehaviorTests {

    @Test
    void rekeyDryRunReadsRekeyBatchWithoutWriting() throws Exception {
        MapperProbe probe = new MapperProbe();

        Object stats = backfill(task(probe), true, true);

        assertEquals(1, value(stats, "scanned"));
        assertEquals(0, value(stats, "updated"));
        assertEquals(1, probe.rekeySelects);
        assertEquals(0, probe.backfillSelects);
        assertEquals(0, probe.rekeyUpdates);
        assertEquals(0, probe.backfillUpdates);
    }

    @Test
    void rekeyWriteUsesRekeyUpdateOnly() throws Exception {
        MapperProbe probe = new MapperProbe();

        Object stats = backfill(task(probe), false, true);

        assertEquals(1, value(stats, "updated"));
        assertEquals(1, probe.rekeyUpdates);
        assertEquals(0, probe.backfillUpdates);
    }

    @Test
    void rekeyWriteRequiresExplicitConfirmation() throws Exception {
        Object task = task(new MapperProbe());
        Method run = task.getClass().getMethod("run", org.springframework.boot.ApplicationArguments.class);

        Exception thrown = assertThrows(Exception.class, () -> run.invoke(task, new DefaultApplicationArguments(
                "phone-privacy-backfill",
                "--table=survey",
                "--tenantId=default",
                "--dryRun=false",
                "--rekey=true"
        )));
        assertEquals(IllegalArgumentException.class, thrown.getCause().getClass());
    }

    @Test
    void backfillKeepsGoingAfterInvalidPhone() throws Exception {
        MapperProbe probe = new MapperProbe(List.of(row(1L, "not-a-phone"), row(2L, "100 0000 0000")));

        Object stats = backfill(task(probe), false, false);

        assertEquals(2, value(stats, "scanned"));
        assertEquals(1, value(stats, "invalid"));
        assertEquals(1, value(stats, "updated"));
        assertEquals(1, probe.backfillUpdates);
    }

    private static Object task(MapperProbe probe) throws Exception {
        Class<?> taskClass = Class.forName("org.example.xyyx.task.PhonePrivacyBackfillTask");
        Class<?> mapperClass = Class.forName("org.example.xyyx.mapper.SurveyMapper");
        Class<?> serviceClass = Class.forName("org.example.xyyx.service.PhonePrivacyService");
        return taskClass.getConstructor(mapperClass, serviceClass, ConfigurableApplicationContext.class)
                .newInstance(probe.mapper(mapperClass), phonePrivacyService(serviceClass), context());
    }

    private static Object backfill(Object task, boolean dryRun, boolean rekey) throws Exception {
        Method method = task.getClass().getDeclaredMethod(
                "backfill", String.class, int.class, boolean.class, boolean.class, boolean.class
        );
        method.setAccessible(true);
        return method.invoke(task, "default", 500, dryRun, true, rekey);
    }

    private static Object phonePrivacyService(Class<?> serviceClass) throws Exception {
        return serviceClass.getMethod("forTesting", String.class, String.class, String.class, String.class)
                .invoke(null,
                        "v1",
                        key("phone-privacy-enc-key-v1-32bytes"),
                        "h1",
                        key("phone-privacy-hmac-key-v1-32byt")
                );
    }

    private static Object row() throws Exception {
        return row(1L, "100 0000 0000");
    }

    private static Object row(Long id, String phone) throws Exception {
        Object survey = Class.forName("org.example.xyyx.entity.Survey").getConstructor().newInstance();
        survey.getClass().getMethod("setId", Long.class).invoke(survey, id);
        survey.getClass().getMethod("setTenantId", String.class).invoke(survey, "default");
        survey.getClass().getMethod("setCustomerUuid", String.class).invoke(survey, "customer-" + id);
        survey.getClass().getMethod("setPhone", String.class).invoke(survey, phone);
        return survey;
    }

    private static ConfigurableApplicationContext context() {
        return (ConfigurableApplicationContext) Proxy.newProxyInstance(
                PhonePrivacyBackfillTaskBehaviorTests.class.getClassLoader(),
                new Class<?>[]{ConfigurableApplicationContext.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object value(Object target, String method) throws Exception {
        Method found = target.getClass().getDeclaredMethod(method);
        found.setAccessible(true);
        return found.invoke(target);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static String key(String value) {
        byte[] bytes = new byte[32];
        byte[] seed = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(seed, 0, bytes, 0, Math.min(seed.length, bytes.length));
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static final class MapperProbe {
        private final List<Object> rows;
        int backfillSelects;
        int rekeySelects;
        int backfillUpdates;
        int rekeyUpdates;

        MapperProbe() throws Exception {
            this(List.of(row()));
        }

        MapperProbe(List<Object> rows) {
            this.rows = rows;
        }

        Object mapper(Class<?> mapperClass) {
            return Proxy.newProxyInstance(
                    PhonePrivacyBackfillTaskBehaviorTests.class.getClassLoader(),
                    new Class<?>[]{mapperClass},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "selectPhoneBackfillBatch" -> {
                            backfillSelects++;
                            yield backfillSelects == 1 ? rows : List.of();
                        }
                        case "selectPhoneRekeyBatch" -> {
                            rekeySelects++;
                            yield rekeySelects == 1 ? rows : List.of();
                        }
                        case "updatePhonePrivacyBackfill" -> {
                            backfillUpdates++;
                            yield 1;
                        }
                        case "updatePhonePrivacyRekey" -> {
                            rekeyUpdates++;
                            yield 1;
                        }
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }
}
