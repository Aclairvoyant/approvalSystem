package com.approval.system.common.utils;

import com.approval.system.common.config.AliyunOssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssUtilsTest {

    @Test
    void uploadBytesWithNullMaxFileSizeDoesNotThrowNullPointerException() {
        AliyunOssProperties properties = new AliyunOssProperties();
        properties.setAllowedFileTypes("image/*");

        OssUtils ossUtils = new OssUtils();
        ReflectionTestUtils.setField(ossUtils, "ossProperties", properties);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> ossUtils.uploadBytes(new byte[]{1, 2, 3}, "voice.wav", "audio/wav"));

        assertFalse(exception.getMessage().contains("NullPointerException"));
        assertFalse(exception.getMessage().contains("Cannot invoke"));
    }
}
