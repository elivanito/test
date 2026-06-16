package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.port.in.FindPotentialSuppliersUseCase.Query;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Cursor;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Filter;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Pagination;
import com.inditex.suppliers.application.util.CursorCodec;
import com.inditex.suppliers.application.util.PotentialSupplierLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindPotentialSuppliersServiceTest {

    private PotentialSupplierQuery queryPort;
    private CursorCodec cursorCodec;
    private FindPotentialSuppliersService service;

    @BeforeEach
    void setup() {
        queryPort = mock(PotentialSupplierQuery.class);
        cursorCodec = new CursorCodec("test-secret-key".getBytes());
        service = new FindPotentialSuppliersService(queryPort, cursorCodec);
    }

    private PotentialSupplierPage emptyPage() {
        return PotentialSupplierPage.ofOffset(List.of(), 5, 0, 0);
    }

    @Test
    void delegates_offset_query_to_port() {
        when(queryPort.findPotential(any(Filter.class))).thenReturn(emptyPage());

        service.find(new Query(500, 5, 0));

        ArgumentCaptor<Filter> captor = ArgumentCaptor.forClass(Filter.class);
        verify(queryPort).findPotential(captor.capture());
        Filter filter = captor.getValue();
        assertThat(filter.rate()).isEqualTo(500);
        assertThat(filter.limit()).isEqualTo(5);
        assertThat(filter.pagination()).isInstanceOf(Pagination.Offset.class);
        assertThat(((Pagination.Offset) filter.pagination()).offset()).isEqualTo(0);
    }

    @Test
    void uses_keyset_pagination_when_cursor_present() {
        Cursor cursor = new Cursor(42.5, 123_456_789L);
        String encoded = cursorCodec.encode(cursor);
        when(queryPort.findPotential(any(Filter.class))).thenReturn(emptyPage());

        service.find(new Query(500, 5, 0, encoded, null, null));

        ArgumentCaptor<Filter> captor = ArgumentCaptor.forClass(Filter.class);
        verify(queryPort).findPotential(captor.capture());
        assertThat(captor.getValue().pagination()).isInstanceOf(Pagination.Keyset.class);
    }

    @Test
    void empty_cursor_string_triggers_keyset_first_page() {
        when(queryPort.findPotential(any(Filter.class))).thenReturn(emptyPage());

        service.find(new Query(500, 5, 0, "", null, null));

        ArgumentCaptor<Filter> captor = ArgumentCaptor.forClass(Filter.class);
        verify(queryPort).findPotential(captor.capture());
        Pagination.Keyset keyset = (Pagination.Keyset) captor.getValue().pagination();
        assertThat(keyset.cursor()).isNull();
    }

    @Test
    void passes_country_filter_uppercased() {
        when(queryPort.findPotential(any(Filter.class))).thenReturn(emptyPage());

        service.find(new Query(500, 5, 0, null, "es", null));

        ArgumentCaptor<Filter> captor = ArgumentCaptor.forClass(Filter.class);
        verify(queryPort).findPotential(captor.capture());
        assertThat(captor.getValue().country()).isEqualTo("ES");
    }

    @Test
    void rejects_rate_below_minimum() {
        assertThatThrownBy(() -> service.find(new Query(PotentialSupplierLimits.MIN_RATE - 1, 5, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate");
    }

    @Test
    void rejects_limit_below_minimum() {
        assertThatThrownBy(() -> service.find(new Query(500, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void rejects_limit_above_maximum() {
        assertThatThrownBy(() -> service.find(
                new Query(500, PotentialSupplierLimits.MAX_LIMIT + 1, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void rejects_negative_offset() {
        assertThatThrownBy(() -> service.find(new Query(500, 5, -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }

    @Test
    void rejects_invalid_country_code() {
        assertThatThrownBy(() -> service.find(new Query(500, 5, 0, null, "XYZ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("country");
    }

    @Test
    void ignores_blank_country() {
        when(queryPort.findPotential(any(Filter.class))).thenReturn(emptyPage());

        service.find(new Query(500, 5, 0, null, "  ", null));

        ArgumentCaptor<Filter> captor = ArgumentCaptor.forClass(Filter.class);
        verify(queryPort).findPotential(captor.capture());
        assertThat(captor.getValue().country()).isNull();
    }
}
