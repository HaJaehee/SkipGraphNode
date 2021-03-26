package skipnode;

/* -------------------------------------------------------- */

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 File name : JedisTest.java
 It is used as a factory pattern to create facade object to use Jedis client.
 Author : Jaehee Ha (jaehee.ha@kaist.ac.kr)
 Creation Date : 2021-03-19
 Version : 1.0.0
 */
/* -------------------------------------------------------- */

public class JedisFactoryTest {

    private Jedis jedis = null;
    private JedisPoolConfig poolConfig = null;
    private JedisPool pool = null;

    public JedisFactoryTest(String poolConfig, String host, int port, int timeout, String password, boolean forcedReset) {

        this.pool = initiate(poolConfig, host, port, timeout, password, forcedReset);
    }

    public JedisFactoryTest(String host, int port, int timeout, String password, boolean forcedReset) {
        this(null, host, port, timeout, password, forcedReset);
    }

    public JedisFactoryTest(String host, int port, int timeout, String password) {
        this(null, host, port, timeout, password, false);
    }

    private JedisPool initiate (String poolConfig, String host, int port, int timeout, String password, boolean forcedReset) {

        JedisPool pool = this.pool;

        if (pool == null || forcedReset == true) {
            if (poolConfig == null) {
                this.poolConfig = new JedisPoolConfig();
            }
            else {
                //TODO
            }
            pool = new JedisPool(this.poolConfig, host, port, timeout, password);
        }

        return pool;
    }

    public JedisPool getJedisPoolInstance(){

        return this.pool;
    }
}
