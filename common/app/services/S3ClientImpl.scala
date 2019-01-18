package services

import java.net.URI
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, Paths}

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.model.{GetObjectRequest, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client, AmazonS3URI}
import com.amazonaws.{AmazonClientException, AmazonServiceException}
import javax.inject.Inject
import play.api.{Configuration, Logger}

import scala.util.{Failure, Success, Try}

class S3ClientImpl @Inject()(configuration: Configuration) extends S3Client {

  private val logger = Logger(this.getClass)

  private val region = Regions.fromName(configuration.get[String]("s3.region"))

  private val s3: AmazonS3 = new AmazonS3Client()
  s3.setRegion(Region.getRegion(region))


  override def download(input: URI, dir: Path): Try[Path] = {
    val s3Uri = new AmazonS3URI(input)
    val bucket = s3Uri.getBucket
    val key = s3Uri.getKey
    val file = key.split("/").last
    val tempInputFile = Paths.get(dir.toString, "input", file)
    //    val tempOutputFile = Paths.get(baseTemp.getAbsolutePath, "output", file)
    try {
      if (!Files.exists(tempInputFile.getParent)) {
        Files.createDirectories(tempInputFile.getParent)
      }
      logger.info(s"Download ${input}")
      val req = new GetObjectRequest(bucket, key)
      val s3Object = s3.getObject(req)
      Files.copy(s3Object.getObjectContent(), tempInputFile, REPLACE_EXISTING)
      Success(tempInputFile)
      //      Success(Work(
      //        tempInputFile.toUri,
      //        tempOutputFile.toUri,
      //        task))
    } catch {
      case ase: AmazonServiceException => {
        logger.error("Caught an AmazonServiceException, which means your request made it to Amazon S3, but was rejected with an error response for some reason.");
        logger.error("Error Message:    " + ase.getMessage());
        logger.error("HTTP Status Code: " + ase.getStatusCode());
        logger.error("AWS Error Code:   " + ase.getErrorCode());
        logger.error("Error Type:       " + ase.getErrorType());
        logger.error("Request ID:       " + ase.getRequestId());
        Failure(ase)
      }
      case ace: AmazonClientException => {
        logger.error("Caught an AmazonClientException, which means the client encountered a serious internal problem while trying to communicate with S3, such as not being able to access the network.");
        logger.error("Error Message: " + ace.getMessage())
        Failure(ace)
      }
      case e: Exception => {
        logger.error("Failed to download: " + e.getMessage())
        Failure(e)
      }
    }
  }

  override def upload(src: Path, output: URI): Try[Unit] = {
    val s3Uri = new AmazonS3URI(output)
    val bucket = s3Uri.getBucket
    val key = s3Uri.getKey
    try {
      logger.info(s"Upload ${src} to ${output}")
      val req = new PutObjectRequest(bucket, key, src.toFile)
      s3.putObject(req)
      Success(())
    } catch {
      case ase: AmazonServiceException => {
        logger.error("Caught an AmazonServiceException, which means your request made it to Amazon S3, but was rejected with an error response for some reason.");
        logger.error("Error Message:    " + ase.getMessage());
        logger.error("HTTP Status Code: " + ase.getStatusCode());
        logger.error("AWS Error Code:   " + ase.getErrorCode());
        logger.error("Error Type:       " + ase.getErrorType());
        logger.error("Request ID:       " + ase.getRequestId());
        Failure(ase)
      }
      case ace: AmazonClientException => {
        logger.error("Caught an AmazonClientException, which means the client encountered a serious internal problem while trying to communicate with S3, such as not being able to access the network.");
        logger.error("Error Message: " + ace.getMessage())
        Failure(ace)
      }
      case e: Exception => {
        logger.error("Failed to upload: " + e.getMessage())
        Failure(e)
      }
    }
  }


}
