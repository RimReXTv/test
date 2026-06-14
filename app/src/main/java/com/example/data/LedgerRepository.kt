package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyPair
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LedgerRepository(private val context: Context, private val dao: LedgerDao) {

    // Active network state
    private val _activeNetwork = MutableStateFlow(NetworkType.TESTNET)
    val activeNetwork: StateFlow<NetworkType> = _activeNetwork

    // Active wallet keys if unlocked/present
    private val _activeKeyPair = MutableStateFlow<KeyPair?>(null)
    val activeKeyPair: StateFlow<KeyPair?> = _activeKeyPair

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress

    private val _syncStatus = MutableStateFlow("Syncing headers...")
    val syncStatus: StateFlow<String> = _syncStatus

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    init {
        // Initial setup for the selected network
        _activeNetwork.value = NetworkType.TESTNET
    }

    /**
     * Swaps the active network environment (0.10, 0.11).
     * Reloads genesis data in separate partitions or wipes/re-initializes variables.
     */
    suspend fun switchNetwork(network: NetworkType) {
        _activeNetwork.value = network
        _syncStatus.value = "Initialising ${network.displayName}..."
        initializeNetworkState(network)
    }

    /**
     * Ensures we have a genesis block and default accounts set up for the specified network (0.5, 0.11, 0.13).
     */
    suspend fun initializeNetworkState(network: NetworkType) = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        // Check if genesis block exists (height = 0)
        val genesis = dao.getBlockByHeight(0, network.chainId)
        if (genesis == null) {
            // Setup Genesis Data
            dao.clearMempool(network.chainId)
            dao.wipeBlocks(network.chainId)

            val genesisValidatorStr = GenesisSpec.getBootstrapValidators(network).firstOrNull()?.first ?: "GENESIS_ROOT"
            
            // Generate a deterministic or custom Genesis block
            val genesisHeader = BlockHeader(
                version = 1,
                chainId = network.chainId,
                height = 0,
                timestamp = 1776268800000L, // Static July 2026 timestamp
                previousHash = "0000000000000000000000000000000000000000000000000000000000000000",
                merkleRoot = CryptoHelper.sha256("genesis_payload"),
                validatorPublicKey = genesisValidatorStr,
                blockSignature = "GENESIS_BOOTSTRAP_SIG"
            )

            val genesisBlock = BlockEntity(
                height = 0,
                hash = genesisHeader.calculateHash(),
                previousHash = genesisHeader.previousHash,
                timestamp = genesisHeader.timestamp,
                validatorPublicKey = genesisHeader.validatorPublicKey,
                signature = genesisHeader.blockSignature,
                transactionCount = 1,
                version = genesisHeader.version,
                chainId = genesisHeader.chainId,
                merkleRoot = genesisHeader.merkleRoot
            )
            
            dao.insertBlock(genesisBlock)

            // Setup Genesis Accounts
            val systemAddress = "AET_SYSTEM_MINING_POOL"
            dao.insertAccount(AccountEntity(systemAddress, 10_000_000_000_000L, 0, 0)) // 10 million AET in microAET

            // Mint initial bootstrap distribution to validator seeds
            GenesisSpec.getBootstrapValidators(network).forEachIndexed { index, (pubKey, host) ->
                val addr = CryptoHelper.getAddressFromPublicKeyString(pubKey)
                dao.insertAccount(AccountEntity(addr, 5_000_000_000L, 0, 1_000_000_000L, true)) // 5,000 AET balance, 1,000 AET split-stake
                
                // Add peer node
                dao.insertPeer(PeerEntity(
                    ipAddress = host,
                    role = "SEED_VALIDATOR",
                    isHealthy = true,
                    lastSeen = System.currentTimeMillis(),
                    displayName = "Seed Validator #${index + 1}"
                ))
            }

            // Insert initial default transactions
            val initialTx = TxEntity(
                hash = CryptoHelper.sha256("genesis_distribution_tx_" + network.chainId),
                blockHeight = 0,
                sender = systemAddress,
                receiver = CryptoHelper.getAddressFromPublicKeyString(genesisValidatorStr),
                amount = 100_000_000,
                fee = 10,
                nonce = 0,
                signature = "SYSTEM_SIGNATURE",
                timestamp = System.currentTimeMillis(),
                status = "CONFIRMED_OK"
            )
            dao.insertTx(initialTx)
        }
        _syncStatus.value = "Synchronised"
        _isSyncing.value = false
    }

    /**
     * Retrieves the data streams reactively (0.21, 0.18).
     */
    fun getBlocksFlow(network: NetworkType): Flow<List<BlockEntity>> {
        return dao.getBlocksFlow(network.chainId)
    }

    fun getMempoolFlow(network: NetworkType): Flow<List<MempoolEntity>> {
        return dao.getMempoolFlow(network.chainId)
    }

    fun getPeersFlow(): Flow<List<PeerEntity>> {
        return dao.getPeersFlow()
    }

    /**
     * Fetch wallet balance in microAET safely.
     */
    suspend fun getWalletBalance(address: String): AccountEntity = withContext(Dispatchers.IO) {
        return@withContext dao.getAccount(address) ?: AccountEntity(address, 0, 0, 0)
    }

    /**
     * Submit transaction to mempool with full anti-spam / anti-abuse checks (0.8, 0.16, 0.7).
     */
    suspend fun submitTransaction(tx: Transaction): Result<String> = withContext(Dispatchers.IO) {
        val network = _activeNetwork.value
        
        // Anti-spam constraint (0.16): Check minimum fee
        if (tx.fee < network.minFee) {
            return@withContext Result.failure(Exception("Transaction fee is below the network minimum of ${network.minFee} microAET."))
        }

        // Validate account balance
        val senderAcc = dao.getAccount(tx.sender)
        if (senderAcc == null || senderAcc.balance < (tx.amount + tx.fee)) {
            return@withContext Result.failure(Exception("Insufficient account balance. Required: ${tx.amount + tx.fee} microAET."))
        }

        // Nonce validation (Replay Protection - 0.7)
        if (tx.nonce <= senderAcc.nonce) {
            return@withContext Result.failure(Exception("Replay protection: invalid nonce. Expected nonce greater than ${senderAcc.nonce}."))
        }

        // Validate signature
        // Locate matching public key of sender if we can, otherwise use standard validation
        val keychainPublic = _activeKeyPair.value?.public
        val pubKeyStr = if (keychainPublic != null && CryptoHelper.getAddressFromPublicKey(keychainPublic) == tx.sender) {
            CryptoHelper.encodeKey(keychainPublic)
        } else {
            // Assume sender has passed its actual encoded public key inside signature parameters or as address
            tx.sender
        }

        // Verify cryptographic integrity
        val ok = tx.verifyTransaction(pubKeyStr, network.chainId)
        if (!ok && tx.sender != "AET_SYSTEM_MINING_POOL") {
            return@withContext Result.failure(Exception("Cryptographic validation failed: Invalid Signature."))
        }

        // Insert into mempool
        val mempoolHash = tx.calculateHash(network.chainId)
        val mempoolItem = MempoolEntity(
            signature = tx.signature,
            sender = tx.sender,
            receiver = tx.receiver,
            amount = tx.amount,
            fee = tx.fee,
            nonce = tx.nonce,
            timestamp = tx.timestamp,
            chainId = network.chainId,
            hash = mempoolHash
        )
        dao.insertMempoolItem(mempoolItem)
        return@withContext Result.success(mempoolHash)
    }

    /**
     * Commits a block proposed by validators (0.4, 0.20).
     * Simulates Proof of Availability, updates state balances, clears mempool inside transactions.
     */
    suspend fun minePendingBlock(proposerPrivateKey: PrivateKey? = null): Result<BlockEntity> = withContext(Dispatchers.IO) {
        val network = _activeNetwork.value
        
        val mempoolItems = dao.getMempoolList(network.chainId)
        if (mempoolItems.isEmpty()) {
            return@withContext Result.failure(Exception("No pending transactions in mempool to validate."))
        }

        val latestBlock = dao.getLatestBlock(network.chainId) ?: return@withContext Result.failure(Exception("Genesis not loaded."))
        val parentHash = latestBlock.hash
        val parentHeight = latestBlock.height
        val nextHeight = parentHeight + 1

        val activeKey = _activeKeyPair.value
        val blockProposerKeyStr = if (activeKey != null) {
            CryptoHelper.encodeKey(activeKey.public)
        } else {
            // Use active bootstrap validator keys from constants (Immutable genesis validators - 0.5)
            GenesisSpec.getBootstrapValidators(network).first().first
        }

        // Group into actual transaction models for Merkle and verify signatures
        val txsToInclude = mempoolItems.take(50).map { item ->
            Transaction(
                sender = item.sender,
                receiver = item.receiver,
                amount = item.amount,
                fee = item.fee,
                nonce = item.nonce,
                timestamp = item.timestamp,
                signature = item.signature
            )
        }

        val merkleRoot = CryptoHelper.calculateMerkleRoot(txsToInclude.map { it.signature })
        
        val header = BlockHeader(
            version = 1,
            chainId = network.chainId,
            height = nextHeight,
            timestamp = System.currentTimeMillis(),
            previousHash = parentHash,
            merkleRoot = merkleRoot,
            validatorPublicKey = blockProposerKeyStr
        )

        // Sign block (0.4, 0.6)
        if (proposerPrivateKey != null) {
            header.signBlock(proposerPrivateKey)
        } else if (activeKey != null) {
            header.signBlock(activeKey.private)
        } else {
            // Bootstrap validator mock-sign for testing if no active wallet key
            header.blockSignature = CryptoHelper.sha256("mock_signed_block_" + System.currentTimeMillis())
        }

        val newBlock = BlockEntity(
            height = nextHeight,
            hash = header.calculateHash(),
            previousHash = header.previousHash,
            timestamp = header.timestamp,
            validatorPublicKey = header.validatorPublicKey,
            signature = header.blockSignature,
            transactionCount = txsToInclude.size,
            version = header.version,
            chainId = header.chainId,
            merkleRoot = header.merkleRoot
        )

        // Insert proposed block
        dao.insertBlock(newBlock)

        // Apply ledger transactions to state accounts (0.13, 0.4)
        txsToInclude.forEach { tx ->
            // Debit Sender balance and update nonce
            val senderAcc = dao.getAccount(tx.sender)
            if (senderAcc != null) {
                dao.insertAccount(
                    senderAcc.copy(
                        balance = senderAcc.balance - tx.amount - tx.fee,
                        nonce = tx.nonce
                    )
                )
            }

            // Credit Receiver balance
            val receiverAcc = dao.getAccount(tx.receiver)
            if (receiverAcc != null) {
                dao.insertAccount(receiverAcc.copy(balance = receiverAcc.balance + tx.amount))
            } else {
                dao.insertAccount(AccountEntity(tx.receiver, tx.amount, 0, 0))
            }

            // Pay Fees to Proposing Validator
            val valAddr = CryptoHelper.getAddressFromPublicKeyString(blockProposerKeyStr)
            val valAcc = dao.getAccount(valAddr)
            if (valAcc != null) {
                dao.insertAccount(valAcc.copy(balance = valAcc.balance + tx.fee))
            } else {
                dao.insertAccount(AccountEntity(valAddr, tx.fee, 0, 0))
            }

            // Add confirmed Tx to main blockchain table
            dao.insertTx(
                TxEntity(
                    hash = tx.calculateHash(network.chainId),
                    blockHeight = nextHeight,
                    sender = tx.sender,
                    receiver = tx.receiver,
                    amount = tx.amount,
                    fee = tx.fee,
                    nonce = tx.nonce,
                    signature = tx.signature,
                    timestamp = tx.timestamp,
                    status = "CONFIRMED_OK"
                )
            )

            // Remove from local mempool
            dao.deleteMempoolItem(tx.signature)
        }

        // Add 50 microAET reward to the proposing validator to incentivise mobile syncing (0.20)
        val minerValAddr = CryptoHelper.getAddressFromPublicKeyString(blockProposerKeyStr)
        val minerAccObj = dao.getAccount(minerValAddr)
        if (minerAccObj != null) {
            dao.insertAccount(minerAccObj.copy(balance = minerAccObj.balance + 50_000L))
        }

        return@withContext Result.success(newBlock)
    }

    /**
     * Staking / Delegation Module rules (0.19)
     * Locks funds to qualify as validator node.
     */
    suspend fun stakeCoins(amount: Long): Result<String> = withContext(Dispatchers.IO) {
        val address = _walletAddress.value ?: return@withContext Result.failure(Exception("Wallet is locked or not created."))
        val account = dao.getAccount(address) ?: return@withContext Result.failure(Exception("Account record missing from ledger."))

        if (account.balance < amount) {
            return@withContext Result.failure(Exception("Insufficient balance to complete stake amount."))
        }

        val updated = account.copy(
            balance = account.balance - amount,
            stakedAmount = account.stakedAmount + amount,
            isValidator = (account.stakedAmount + amount) >= 100_000_000L // Min 100 AET to acts as Validator
        )
        dao.insertAccount(updated)

        // Mock delegate transaction record
        val txId = CryptoHelper.sha256("delegation_stake_" + System.currentTimeMillis())
        dao.insertTx(
            TxEntity(
                hash = txId,
                blockHeight = -1, // Pending block height until validator commits
                sender = address,
                receiver = "AET_STAKING_CONTRACT",
                amount = amount,
                fee = 10,
                nonce = account.nonce + 1,
                signature = "STAKE_VOTE_SIG",
                timestamp = System.currentTimeMillis(),
                status = "STAKED_ACTIVE"
            )
        )
        return@withContext Result.success(txId)
    }

    suspend fun unstakeCoins(): Result<String> = withContext(Dispatchers.IO) {
        val address = _walletAddress.value ?: return@withContext Result.failure(Exception("Wallet is locked or not created."))
        val account = dao.getAccount(address) ?: return@withContext Result.failure(Exception("Account record missing from ledger."))

        val amountToRelease = account.stakedAmount
        if (amountToRelease <= 0) {
            return@withContext Result.failure(Exception("No active stake found on this account."))
        }

        val updated = account.copy(
            balance = account.balance + amountToRelease,
            stakedAmount = 0L,
            isValidator = false
        )
        dao.insertAccount(updated)

        // Mock unstake log transaction
        val txId = CryptoHelper.sha256("delegation_unstake_" + System.currentTimeMillis())
        dao.insertTx(
            TxEntity(
                hash = txId,
                blockHeight = -1,
                sender = "AET_STAKING_CONTRACT",
                receiver = address,
                amount = amountToRelease,
                fee = 10,
                nonce = account.nonce + 1,
                signature = "UNSTAKE_VOTE_SIG",
                timestamp = System.currentTimeMillis(),
                status = "CONFIRMED_OK"
            )
        )
        return@withContext Result.success(txId)
    }

    /**
     * Secure export & wallet backup recovery module (0.14).
     * Compiles keys to encrypted JSON backups.
     */
    fun createAESEncryptedBackup(passphrase: String): String {
        val keys = _activeKeyPair.value ?: throw Exception("Wallet must be instantiated to backup keys")
        val privateKeyHexStr = CryptoHelper.encodeKey(keys.private)
        val publicKeyHexStr = CryptoHelper.encodeKey(keys.public)

        val jsonObj = JSONObject().apply {
            put("pub", publicKeyHexStr)
            put("priv", privateKeyHexStr)
            put("address", _walletAddress.value)
            put("exportTime", System.currentTimeMillis())
        }

        // Standard robust AES PBKDF or simplified SHA256 keying for backup integrity checking
        val secretKey = SecretKeySpec(CryptoHelper.sha256(passphrase).substring(0, 16).toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = IvParameterSpec("AET_SECRET_IV_99".toByteArray()) // Static validation IV

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        val encryptedBytes = cipher.doFinal(jsonObj.toString().toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    /**
     * Restore from backup (0.14).
     */
    fun restoreKeysFromBackup(encryptedPayload: String, passphrase: String): Result<KeyPair> {
        return try {
            val secretKey = SecretKeySpec(CryptoHelper.sha256(passphrase).substring(0, 16).toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = IvParameterSpec("AET_SECRET_IV_99".toByteArray())

            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedPayload, Base64.DEFAULT))
            val json = JSONObject(String(decryptedBytes))

            val pubStr = json.getString("pub")
            val privStr = json.getString("priv")

            val pubBytes = Base64.decode(pubStr, Base64.DEFAULT)
            val privBytes = Base64.decode(privStr, Base64.DEFAULT)

            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val publicKeySpec = X509EncodedKeySpec(pubBytes)
            val privateKeySpec = PKCS8EncodedKeySpec(privBytes)

            val pubKey = keyFactory.generatePublic(publicKeySpec)
            val privKey = keyFactory.generatePrivate(privateKeySpec)

            val keyPair = KeyPair(pubKey, privKey)
            _activeKeyPair.value = keyPair
            _walletAddress.value = CryptoHelper.getAddressFromPublicKey(pubKey)
            Result.success(keyPair)
        } catch (e: Exception) {
            Result.failure(Exception("Verification failed: Incorrect passphrase or corrupted backup format."))
        }
    }

    /**
     * Create / Initialize new keypair wallet.
     */
    suspend fun createNewWallet(): KeyPair = withContext(Dispatchers.Default) {
        val keys = CryptoHelper.generateKeyPair()
        _activeKeyPair.value = keys
        val address = CryptoHelper.getAddressFromPublicKey(keys.public)
        _walletAddress.value = address

        // Write to ledger DB with default balance
        dao.insertAccount(AccountEntity(address = address, balance = 10_000_000L, nonce = 0, stakedAmount = 0)) // 10 testnet tokens credited (10 million microAET)
        return@withContext keys
    }

    /**
     * RPC API Simulation Layer (0.17).
     * Responses strictly comply with the requested specs in RPC form.
     */
    suspend fun executeRpc(method: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val response = JSONObject()
        val network = _activeNetwork.value
        try {
            when (method) {
                "getBalance" -> {
                    val addr = params.getString("address")
                    val acc = dao.getAccount(addr)
                    response.put("status", "SUCCESS")
                    response.put("balance_microAET", acc?.balance ?: 0)
                    response.put("staked_microAET", acc?.stakedAmount ?: 0)
                }
                "getTransaction" -> {
                    val hash = params.getString("hash")
                    val tx = dao.getTxByHash(hash)
                    if (tx != null) {
                        response.put("status", "SUCCESS")
                        response.put("transaction", JSONObject().apply {
                            put("hash", tx.hash)
                            put("sender", tx.sender)
                            put("receiver", tx.receiver)
                            put("amount", tx.amount)
                            put("fee", tx.fee)
                            put("timestamp", tx.timestamp)
                            put("blockHeight", tx.blockHeight)
                            put("status", tx.status)
                        })
                    } else {
                        response.put("status", "ERROR")
                        response.put("message", "Transaction not found")
                    }
                }
                "getBlock" -> {
                    val height = params.getLong("height")
                    val block = dao.getBlockByHeight(height, network.chainId)
                    if (block != null) {
                        response.put("status", "SUCCESS")
                        response.put("block", JSONObject().apply {
                            put("height", block.height)
                            put("hash", block.hash)
                            put("previousHash", block.previousHash)
                            put("timestamp", block.timestamp)
                            put("validator", block.validatorPublicKey)
                            put("chainId", block.chainId)
                        })
                    } else {
                        response.put("status", "ERROR")
                        response.put("message", "Block not found")
                    }
                }
                "sendTransaction" -> {
                    val sender = params.getString("sender")
                    val receiver = params.getString("receiver")
                    val amount = params.getLong("amount")
                    val fee = params.getLong("fee")
                    val nonce = params.getLong("nonce")
                    
                    val dummyTx = Transaction(sender, receiver, amount, fee, nonce, System.currentTimeMillis())
                    
                    val activeKey = _activeKeyPair.value
                    if (activeKey != null && CryptoHelper.getAddressFromPublicKey(activeKey.public) == sender) {
                        dummyTx.signTransaction(activeKey.private, network.chainId)
                        val res = submitTransaction(dummyTx)
                        if (res.isSuccess) {
                            response.put("status", "SUCCESS")
                            response.put("txHash", res.getOrThrow())
                        } else {
                            response.put("status", "ERROR")
                            response.put("message", res.exceptionOrNull()?.message)
                        }
                    } else {
                        response.put("status", "ERROR")
                        response.put("message", "Signing keys not available for RPC request source.")
                    }
                }
                "getSyncStatus" -> {
                    response.put("status", "SUCCESS")
                    val latest = dao.getLatestBlock(network.chainId)
                    response.put("syncedHeight", latest?.height ?: 0)
                    response.put("isSyncing", _isSyncing.value)
                    response.put("syncStatus", _syncStatus.value)
                    response.put("network", network.chainId)
                }
                "getGenesisInfo" -> {
                    response.put("status", "SUCCESS")
                    val validators = GenesisSpec.getBootstrapValidators(network).map { it.first }
                    response.put("chainId", network.chainId)
                    response.put("genesisTime", 1776268800000L)
                    response.put("version", 1)
                    response.put("activeNetwork", network.displayName)
                }
                else -> {
                    response.put("status", "ERROR")
                    response.put("message", "Unknown method spec name.")
                }
            }
        } catch (e: Exception) {
            response.put("status", "ERROR")
            response.put("message", e.message)
        }
        return@withContext response
    }
}
