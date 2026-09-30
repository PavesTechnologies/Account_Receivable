package com.AccountReceivableManagement.service_Imple.client;

import com.AccountReceivableManagement.dto.client.ClientBudgetSummaryResponseDto;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.client_entity.ClientBudgetSummary;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.client.ClientBudgetSummaryRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientBudgetSummaryServiceImplTest {

    @Mock
    private ClientBudgetSummaryRepository clientBudgetSummaryRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ProjectMasterReferenceRepository projectMasterReferenceRepository;

    @InjectMocks
    private ClientBudgetSummaryServiceImpl clientBudgetSummaryService;

    private UUID clientId;
    private Client client;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        client = Client.builder()
                .clientId(clientId)
                .clientName("Test Client")
                .build();
    }

    @Test
    void testRefreshClientBudget_ClientWithOneUSDProject() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(1L);
        
        Object[] budgetRow = {"USD", new BigDecimal("67000.00"), 1L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(budgetRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "USD"))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of());

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository).save(any(ClientBudgetSummary.class));
        verify(clientBudgetSummaryRepository, never()).delete(any());
    }

    @Test
    void testRefreshClientBudget_ClientWithMultipleUSDProjects() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(2L);
        
        Object[] budgetRow = {"USD", new BigDecimal("100000.00"), 2L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(budgetRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "USD"))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of());

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository).save(any(ClientBudgetSummary.class));
    }

    @Test
    void testRefreshClientBudget_ClientWithUSDAndINRProjects() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(3L);
        
        Object[] usdRow = {"USD", new BigDecimal("67000.00"), 1L};
        Object[] inrRow = {"INR", new BigDecimal("150000.00"), 2L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(usdRow);
        budgetRows.add(inrRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "USD"))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "INR"))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of());

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository, times(2)).save(any(ClientBudgetSummary.class));
    }

    @Test
    void testRefreshClientBudget_ClientWithUSDINRAndEURProjects() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(3L);
        
        Object[] usdRow = {"USD", new BigDecimal("67000.00"), 1L};
        Object[] inrRow = {"INR", new BigDecimal("500000.00"), 1L};
        Object[] eurRow = {"EUR", new BigDecimal("20000.00"), 1L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(usdRow);
        budgetRows.add(inrRow);
        budgetRows.add(eurRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(any(), any()))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of());

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository, times(3)).save(any(ClientBudgetSummary.class));
    }

    @Test
    void testRefreshClientBudget_RemoveCurrencyWithNoProjects() {
        ClientBudgetSummary existingUSD = ClientBudgetSummary.builder()
                .summaryId(UUID.randomUUID())
                .client(client)
                .currency("USD")
                .totalBudget(new BigDecimal("50000.00"))
                .projectCount(1L)
                .build();
        
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(2L);
        
        Object[] inrRow = {"INR", new BigDecimal("150000.00"), 2L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(inrRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "INR"))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of(existingUSD));

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository).delete(existingUSD);
    }

    @Test
    void testRefreshClientBudget_ClientWithNoProjects() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(0L);
        
        ClientBudgetSummary existingSummary = ClientBudgetSummary.builder()
                .summaryId(UUID.randomUUID())
                .client(client)
                .currency("USD")
                .build();
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of(existingSummary));

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository).delete(existingSummary);
        verify(projectMasterReferenceRepository, never()).getBudgetSummaryByCurrency(any());
    }

    @Test
    void testRefreshClientBudget_ClientNotFound() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThrows(GlobalExceptionHandler.ResourceNotFoundException.class,
                () -> clientBudgetSummaryService.refreshClientBudget(clientId));
    }

    @Test
    void testRefreshClientBudget_UpdateExistingSummary() {
        ClientBudgetSummary existingUSD = ClientBudgetSummary.builder()
                .summaryId(UUID.randomUUID())
                .client(client)
                .currency("USD")
                .totalBudget(new BigDecimal("50000.00"))
                .projectCount(1L)
                .build();
        
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(2L);
        
        Object[] usdRow = {"USD", new BigDecimal("100000.00"), 2L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(usdRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "USD"))
                .thenReturn(Optional.of(existingUSD));
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of(existingUSD));

        clientBudgetSummaryService.refreshClientBudget(clientId);

        assertEquals(new BigDecimal("100000.00"), existingUSD.getTotalBudget());
        assertEquals(2L, existingUSD.getProjectCount());
        verify(clientBudgetSummaryRepository).save(existingUSD);
    }

    @Test
    void testGetClientBudget_MultiCurrency() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        
        ClientBudgetSummary usdSummary = ClientBudgetSummary.builder()
                .summaryId(UUID.randomUUID())
                .client(client)
                .currency("USD")
                .totalBudget(new BigDecimal("67000.00"))
                .projectCount(1L)
                .lastCalculatedAt(LocalDateTime.now())
                .build();
        
        ClientBudgetSummary inrSummary = ClientBudgetSummary.builder()
                .summaryId(UUID.randomUUID())
                .client(client)
                .currency("INR")
                .totalBudget(new BigDecimal("150000.00"))
                .projectCount(2L)
                .lastCalculatedAt(LocalDateTime.now())
                .build();
        
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of(usdSummary, inrSummary));

        ClientBudgetSummaryResponseDto response = clientBudgetSummaryService.getClientBudget(clientId);

        assertNotNull(response);
        assertEquals(clientId, response.getClientId());
        assertEquals("Test Client", response.getClientName());
        assertEquals(2, response.getBudgets().size());
        
        ClientBudgetSummaryResponseDto.CurrencyBudget usdBudget = response.getBudgets().stream()
                .filter(b -> "USD".equals(b.getCurrency()))
                .findFirst()
                .orElse(null);
        assertNotNull(usdBudget);
        assertEquals("USD", usdBudget.getCurrency());
        assertEquals(new BigDecimal("67000.00"), usdBudget.getTotalProjectBudget());
        assertEquals(1L, usdBudget.getProjectCount());
        
        ClientBudgetSummaryResponseDto.CurrencyBudget inrBudget = response.getBudgets().stream()
                .filter(b -> "INR".equals(b.getCurrency()))
                .findFirst()
                .orElse(null);
        assertNotNull(inrBudget);
        assertEquals("INR", inrBudget.getCurrency());
        assertEquals(new BigDecimal("150000.00"), inrBudget.getTotalProjectBudget());
        assertEquals(2L, inrBudget.getProjectCount());
    }

    @Test
    void testGetClientBudget_ClientNotFound() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThrows(GlobalExceptionHandler.ResourceNotFoundException.class,
                () -> clientBudgetSummaryService.getClientBudget(clientId));
    }

    @Test
    void testGetClientBudget_NoSummariesFound() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of());

        assertThrows(GlobalExceptionHandler.ResourceNotFoundException.class,
                () -> clientBudgetSummaryService.getClientBudget(clientId));
    }

    @Test
    void testRefreshClientBudget_NullBudgetHandled() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(projectMasterReferenceRepository.countByClientId(clientId)).thenReturn(1L);
        
        Object[] budgetRow = {"USD", null, 1L};
        List<Object[]> budgetRows = new ArrayList<>();
        budgetRows.add(budgetRow);
        when(projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId))
                .thenReturn(budgetRows);
        
        when(clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, "USD"))
                .thenReturn(Optional.empty());
        when(clientBudgetSummaryRepository.findByClient_ClientId(clientId))
                .thenReturn(List.of());

        clientBudgetSummaryService.refreshClientBudget(clientId);

        verify(clientBudgetSummaryRepository).save(argThat(summary -> 
                summary.getTotalBudget().equals(BigDecimal.ZERO)));
    }
}
