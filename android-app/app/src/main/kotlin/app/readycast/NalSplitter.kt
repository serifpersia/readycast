package app.readycast

class NalSplitter {
    private var buf = ByteArray(0)
    private var segStart = -1
    private var scan = 0

    private fun append(data: ByteArray, len: Int) {
        val n = ByteArray(buf.size + len)
        System.arraycopy(buf, 0, n, 0, buf.size)
        System.arraycopy(data, 0, n, buf.size, len)
        buf = n
    }

    private fun startAt(i: Int): Int {
        if (i + 3 < buf.size && buf[i] == 0.toByte() && buf[i + 1] == 0.toByte()) {
            if (buf[i + 2] == 1.toByte()) return 3
            if (i + 4 < buf.size && buf[i + 2] == 0.toByte() && buf[i + 3] == 1.toByte()) return 4
        }
        return 0
    }

    fun feed(data: ByteArray, len: Int = data.size): List<ByteArray> {
        append(data, len)
        val out = mutableListOf<ByteArray>()
        var i = scan
        while (i < buf.size) {
            val sc = startAt(i)
            if (sc > 0) {
                if (segStart >= 0 && i > segStart) out.add(buf.copyOfRange(segStart, i))
                segStart = i + sc
                i += sc
            } else i++
        }
        scan = i
        if (segStart > 0) {
            buf = buf.copyOfRange(segStart, buf.size)
            scan -= segStart
            segStart = 0
        } else if (segStart < 0 && buf.size > 4) {
            buf = buf.copyOfRange(buf.size - 3, buf.size)
            scan = 3
        }
        return out.filter { it.isNotEmpty() }
    }
}
