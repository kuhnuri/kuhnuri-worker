package services

import java.net.URI

object S3Utils {

  def parse(uri: URI): (String, String) = {
    return (uri.getAuthority, uri.getPath.substring(1))
  }

}
