package services

import java.net.URI

object S3Utils {

  def parse(uri: URI): (String, String) = {
    if (uri.getScheme != "s3") {
      throw new IllegalArgumentException(s"Invalid S3 URI: ${uri}")
    }
    return (uri.getAuthority, uri.getPath.substring(1))
  }

}
