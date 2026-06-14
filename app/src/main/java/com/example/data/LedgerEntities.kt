package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey val height: Long,
    val hash: String,
    val previousHash: String,
    val timestamp: Long,
    val validatorPublicKey: String,
    val signature: String,
    val transactionCount: Int,
    val version: Int,
    val chainId: String,
    val merkleRoot: String
)

@Entity(tableName = "transactions", indices = [Index(value = ["blockHeight"]), Index(value = ["sender"]), Index(value = ["receiver"])])
data class TxEntity(
    @PrimaryKey val hash: String,
    val blockHeight: Long,
    val sender: String,
    val receiver: String,
    val amount: Long,
    val fee: Long,
    val nonce: Long,
    val signature: String,
    val timestamp: Long,
    val status: String // "CONFIRMED_OK", "REJECTED_BAD_SIG", "SPAM_FILTERED"
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val address: String,
    val balance: Long, // microAET
    val nonce: Long,
    val stakedAmount: Long, // microAET staked
    val isValidator: Boolean = false
)

@Entity(tableName = "mempool")
data class MempoolEntity(
    @PrimaryKey val signature: String, // Treat signature as primary unique key
    val sender: String,
    val receiver: String,
    val amount: Long,
    val fee: Long,
    val nonce: Long,
    val timestamp: Long,
    val chainId: String,
    val hash: String
)

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val ipAddress: String,
    val role: String, // "SEED", "VALIDATOR", "LIGHT_MOBILE"
    val isHealthy: Boolean,
    val lastSeen: Long,
    val displayName: String
)

@Dao
interface LedgerDao {
    // --- Blocks Queries ---
    @Query("SELECT * FROM blocks WHERE chainId = :chainId ORDER BY height DESC")
    fun getBlocksFlow(chainId: String): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE chainId = :chainId ORDER BY height DESC LIMIT 1")
    suspend fun getLatestBlock(chainId: String): BlockEntity?

    @Query("SELECT * FROM blocks WHERE height = :height AND chainId = :chainId")
    suspend fun getBlockByHeight(height: Long, chainId: String): BlockEntity?

    @Query("SELECT * FROM blocks WHERE hash = :hash AND chainId = :chainId")
    suspend fun getBlockByHash(hash: String, chainId: String): BlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: BlockEntity)

    // --- Transactions Queries ---
    @Query("SELECT * FROM transactions WHERE hash = :hash")
    suspend fun getTxByHash(hash: String): TxEntity?

    @Query("SELECT * FROM transactions WHERE blockHeight = :height ORDER BY timestamp DESC")
    fun getTxsInBlockFlow(height: Long): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTxsFlow(limit: Int): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions WHERE sender = :address OR receiver = :address ORDER BY timestamp DESC")
    fun getTxsForAddressFlow(address: String): Flow<List<TxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTx(tx: TxEntity)

    // --- Accounts Queries ---
    @Query("SELECT * FROM accounts WHERE address = :address")
    suspend fun getAccount(address: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY balance DESC")
    fun getRichListFlow(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    // --- Mempool Queries ---
    @Query("SELECT * FROM mempool WHERE chainId = :chainId ORDER BY fee DESC, timestamp ASC")
    fun getMempoolFlow(chainId: String): Flow<List<MempoolEntity>>

    @Query("SELECT * FROM mempool WHERE chainId = :chainId ORDER BY fee DESC, timestamp ASC")
    suspend fun getMempoolList(chainId: String): List<MempoolEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMempoolItem(item: MempoolEntity)

    @Query("DELETE FROM mempool WHERE signature = :signature")
    suspend fun deleteMempoolItem(signature: String)

    @Query("DELETE FROM mempool WHERE chainId = :chainId")
    suspend fun clearMempool(chainId: String)

    // --- Peers ---
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getPeersFlow(): Flow<List<PeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: PeerEntity)

    // --- Global Wipe ---
    @Query("DELETE FROM blocks WHERE chainId = :chainId")
    suspend fun wipeBlocks(chainId: String)

    @Query("DELETE FROM transactions")
    suspend fun wipeTransactions()
}
