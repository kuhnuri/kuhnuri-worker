package services

import javax.inject.Inject
import javax.xml.XMLConstants
import javax.xml.stream.{XMLInputFactory, XMLStreamConstants}
import play.api.Configuration

import scala.collection.mutable

class DitaOtTranstypesConf @Inject()(configuration: Configuration) extends TranstypeConf {

  override def get: Set[String] = {
    val available = mutable.Buffer[String]()
    val reader = XMLInputFactory
      .newInstance()
      .createXMLStreamReader(getClass.getResourceAsStream("/plugins.xml"))
    while (reader.hasNext) {
      reader.next() match {
        case XMLStreamConstants.START_ELEMENT if reader.getLocalName == "transtype" =>
          available += reader.getAttributeValue(XMLConstants.NULL_NS_URI, "name")
        case _ =>
      }
    }
    val transtypes = configuration.get[Seq[String]]("worker.transtypes")
    transtypes
      .find(transtype => !available.contains(transtype))
      .foreach(
        transtype =>
          throw new IllegalArgumentException(s"Configured transtype not available: ${transtype}")
      )
    transtypes.toSet
  }

}
