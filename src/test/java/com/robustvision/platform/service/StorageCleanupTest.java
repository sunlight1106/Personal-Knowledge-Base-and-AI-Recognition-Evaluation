package com.robustvision.platform.service;
import com.robustvision.platform.common.BusinessException;
import io.minio.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class StorageCleanupTest {
 @TempDir Path temporary;
 @Test void remoteCleanupFailureMustNotBeAcknowledged()throws Exception{
  var storage=new MinioObjectStorageService("http://127.0.0.1:1","fixture-access","fixture-secret","fixture-bucket");
  var client=mock(MinioClient.class);ReflectionTestUtils.setField(storage,"client",client);
  doThrow(new IllegalStateException("synthetic offline storage")).when(client).removeObject(any(RemoveObjectArgs.class));
  assertThatThrownBy(()->storage.deleteChecked("fixture")).isInstanceOf(BusinessException.class);
  assertThatCode(()->storage.delete("fixture")).doesNotThrowAnyException();
 }
 @Test void localCleanupFailureIsRetryableAndMissingFilesAreIdempotent()throws Exception{
  var storage=new LocalObjectStorageService(temporary.toString());
  Files.createDirectory(temporary.resolve("occupied"));Files.writeString(temporary.resolve("occupied/child"),"fixture");
  assertThatThrownBy(()->storage.deleteChecked("occupied")).isInstanceOf(BusinessException.class);
  assertThatCode(()->storage.deleteChecked("missing")).doesNotThrowAnyException();
 }
}
