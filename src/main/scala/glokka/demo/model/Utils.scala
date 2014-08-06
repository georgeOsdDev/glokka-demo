package glokka.demo.model

object ErrorCode {
  val SUCCESS        = 0
  val INVALID_APIKEY = 1
  val NOT_CONNECTED  = 2
  val INVALID_TAG    = 3
  val INVALID_CMD    = 4

}

object Utils {
  val KEY_AUTH  = md5("hello")

  def auth(key: String): Boolean = {
    md5(key) == KEY_AUTH
  }

  def md5(s: String) = {
    import java.security.MessageDigest
    MessageDigest.getInstance("MD5").digest(s.getBytes).map("%02x".format(_)).mkString
  }
}