package com.giovannimenzano.jblockchain.services;

import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.exceptions.BlockchainException;
import com.giovannimenzano.jblockchain.services.impl.NetworkServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkServiceTest {

	@Mock
	private IBlockchainService blockchainService;

	@Mock
	private RestTemplate restTemplate;

	private NetworkServiceImpl networkService;

	@BeforeEach
	void setUp() {
		networkService = new NetworkServiceImpl(blockchainService, restTemplate);
		ReflectionTestUtils.setField(networkService, "nodeUrl", "http://localhost");
		ReflectionTestUtils.setField(networkService, "serverPort", 8091);
		ReflectionTestUtils.setField(networkService, "contextPath", "/jblockchain");
		ReflectionTestUtils.setField(networkService, "networkBasePath", "/api/network");
		ReflectionTestUtils.setField(networkService, "blockchainBasePath", "/api/chain");
		ReflectionTestUtils.setField(networkService, "statusEndpoint", "/status");
		ReflectionTestUtils.setField(networkService, "broadcastEndpoint", "/broadcast");
		ReflectionTestUtils.setField(networkService, "maxBroadcastPeers", 0);
		ReflectionTestUtils.setField(networkService, "maxConsecutiveFailures", 3);
		ReflectionTestUtils.setField(networkService, "seedNodesConfig", "");
	}

	@Nested
	@DisplayName("Node Registration")
	class NodeRegistration {

		@Test
		@DisplayName("registerNode adds a peer to the set")
		void registerNode_addsPeer() {
			networkService.registerNode("http://peer1:8092/jblockchain");

			Set<String> nodes = networkService.getNodes();
			assertEquals(1, nodes.size());
			assertTrue(nodes.contains("http://peer1:8092/jblockchain"));
		}

		@Test
		@DisplayName("registerNode rejects blank URL")
		void registerNode_rejectsBlank() {
			assertThrows(BlockchainException.class, () -> networkService.registerNode(""));
		}

		@Test
		@DisplayName("registerNode rejects null URL")
		void registerNode_rejectsNull() {
			assertThrows(BlockchainException.class, () -> networkService.registerNode(null));
		}

		@Test
		@DisplayName("registerNode ignores duplicate URLs")
		void registerNode_ignoresDuplicates() {
			networkService.registerNode("http://peer1:8092/jblockchain");
			networkService.registerNode("http://peer1:8092/jblockchain");

			assertEquals(1, networkService.getNodes().size());
		}

		@Test
		@DisplayName("registerNode trims whitespace")
		void registerNode_trimsWhitespace() {
			networkService.registerNode("  http://peer1:8092/jblockchain  ");

			assertTrue(networkService.getNodes().contains("http://peer1:8092/jblockchain"));
		}
	}

	@Nested
	@DisplayName("Node Removal")
	class NodeRemoval {

		@Test
		@DisplayName("removeNode removes existing peer")
		void removeNode_removesPeer() {
			networkService.registerNode("http://peer1:8092/jblockchain");
			networkService.removeNode("http://peer1:8092/jblockchain");

			assertTrue(networkService.getNodes().isEmpty());
		}

		@Test
		@DisplayName("removeNode ignores unknown peer")
		void removeNode_ignoresUnknown() {
			networkService.registerNode("http://peer1:8092/jblockchain");
			networkService.removeNode("http://unknown:9999/jblockchain");

			assertEquals(1, networkService.getNodes().size());
		}

		@Test
		@DisplayName("removeNode handles null gracefully")
		void removeNode_handlesNull() {
			assertDoesNotThrow(() -> networkService.removeNode(null));
		}
	}

	@Nested
	@DisplayName("Peer Eviction")
	class PeerEviction {

		@Test
		@DisplayName("recordPeerFailure evicts after max consecutive failures")
		void recordPeerFailure_evictsAfterThreshold() {
			String peer = "http://peer1:8092/jblockchain";
			networkService.registerNode(peer);

			networkService.recordPeerFailure(peer); // 1/3
			assertTrue(networkService.getNodes().contains(peer));

			networkService.recordPeerFailure(peer); // 2/3
			assertTrue(networkService.getNodes().contains(peer));

			networkService.recordPeerFailure(peer); // 3/3 → evicted
			assertFalse(networkService.getNodes().contains(peer));
		}

		@Test
		@DisplayName("recordPeerSuccess resets failure counter")
		void recordPeerSuccess_resetsCounter() {
			String peer = "http://peer1:8092/jblockchain";
			networkService.registerNode(peer);

			networkService.recordPeerFailure(peer); // 1/3
			networkService.recordPeerFailure(peer); // 2/3
			networkService.recordPeerSuccess(peer); // reset

			// Should need 3 more failures to evict
			networkService.recordPeerFailure(peer); // 1/3
			networkService.recordPeerFailure(peer); // 2/3
			assertTrue(networkService.getNodes().contains(peer));

			networkService.recordPeerFailure(peer); // 3/3 → evicted
			assertFalse(networkService.getNodes().contains(peer));
		}
	}

	@Nested
	@DisplayName("Local Node URL")
	class LocalNodeUrl {

		@Test
		@DisplayName("getLocalNodeUrl assembles correctly")
		void getLocalNodeUrl_assembles() {
			assertEquals("http://localhost:8091/jblockchain", networkService.getLocalNodeUrl());
		}
	}

	@Nested
	@DisplayName("Broadcast")
	class Broadcast {

		@Test
		@DisplayName("broadcastBlock sends to all peers")
		void broadcastBlock_sendsToAll() {
			networkService.registerNode("http://peer1:8092/jblockchain");
			networkService.registerNode("http://peer2:8093/jblockchain");

			Block block = new Block(1, LocalDateTime.now(), "test", "prevhash");

			networkService.broadcastBlock(block);

			verify(restTemplate, times(2)).postForObject(anyString(), any(Block.class), eq(Void.class));
		}

		@Test
		@DisplayName("broadcastBlock does nothing with no peers")
		void broadcastBlock_noPeers() {
			Block block = new Block(1, LocalDateTime.now(), "test", "prevhash");

			networkService.broadcastBlock(block);

			verify(restTemplate, never()).postForObject(anyString(), any(), any());
		}

		@Test
		@DisplayName("broadcastBlock records failure on RestClientException")
		void broadcastBlock_recordsFailure() {
			String peer = "http://peer1:8092/jblockchain";
			networkService.registerNode(peer);

			Block block = new Block(1, LocalDateTime.now(), "test", "prevhash");
			when(restTemplate.postForObject(anyString(), any(Block.class), eq(Void.class)))
					.thenThrow(new RestClientException("Connection refused"));

			// 3 broadcasts → 3 failures → eviction
			networkService.broadcastBlock(block);
			networkService.broadcastBlock(block);
			networkService.broadcastBlock(block);

			assertTrue(networkService.getNodes().isEmpty());
		}
	}

	@Nested
	@DisplayName("Consensus")
	class Consensus {

		@Test
		@DisplayName("resolveConflicts returns false with no peers")
		void resolveConflicts_noPeers() {
			assertFalse(networkService.resolveConflicts());
		}

		@Test
		@DisplayName("resolveConflicts evicts unreachable peer after threshold")
		void resolveConflicts_evictsUnreachable() {
			String peer = "http://dead:9999/jblockchain";
			networkService.registerNode(peer);

			Block genesis = new Block(0, LocalDateTime.now(), "Genesis", "0");
			when(blockchainService.getChain()).thenReturn(List.of(genesis));
			when(restTemplate.getForObject(anyString(), any()))
					.thenThrow(new RestClientException("Connection refused"));

			// 3 consensus rounds → 3 failures → evicted
			networkService.resolveConflicts();
			networkService.resolveConflicts();
			networkService.resolveConflicts();

			assertTrue(networkService.getNodes().isEmpty());
		}
	}

	@Nested
	@DisplayName("getNodes immutability")
	class GetNodes {

		@Test
		@DisplayName("getNodes returns unmodifiable set")
		void getNodes_unmodifiable() {
			networkService.registerNode("http://peer1:8092/jblockchain");
			Set<String> nodes = networkService.getNodes();

			assertThrows(UnsupportedOperationException.class, () -> nodes.add("http://hacker:666"));
		}
	}
}
