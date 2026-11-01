package dev.gavenda.yuuka.ui.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.Holding
import dev.gavenda.yuuka.domain.TradeKinds
import dev.gavenda.yuuka.domain.boardLot
import dev.gavenda.yuuka.domain.currencySymbol
import dev.gavenda.yuuka.domain.dividendTax
import dev.gavenda.yuuka.domain.parsePrice
import dev.gavenda.yuuka.domain.parseShares
import dev.gavenda.yuuka.domain.positions
import dev.gavenda.yuuka.domain.priceInput
import dev.gavenda.yuuka.domain.pseFees
import dev.gavenda.yuuka.domain.valueOf
import dev.gavenda.yuuka.ui.common.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val KINDS = listOf(TradeKinds.BUY, TradeKinds.SELL, TradeKinds.DIVIDEND)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** The time of day an `occurredOn` names, or null when it names only the day. */
internal fun timeOf(occurredOn: String): LocalTime? =
    if (occurredOn.length > 10) runCatching { LocalTime.parse(occurredOn.substring(11, 16)) }.getOrNull() else null

/**
 * A buy, a sell or a cash dividend, as a full-screen form. The stock comes first and fills in the broker account
 * that holds it and its last close; the fees a PSE broker charges (or a dividend's final tax) are worked out as
 * the shares and price are typed, so what the trade costs or brings in is known before it is saved. Given
 * [editing], it opens on that trade and puts it right, its cash leg with it.
 */
@Composable
fun TradeForm(
    state: InvestmentsUiState,
    onSave: suspend (TradeSubmission) -> Unit,
    onDismiss: () -> Unit,
    editing: Trade? = null,
    initialSymbol: String? = null,
    initialAccount: String? = null,
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    val savedMessage = stringResource(if (editing != null) R.string.investments_trade_updated else R.string.investments_trade_added)

    var kind by remember { mutableStateOf(editing?.kind ?: TradeKinds.BUY) }
    var symbol by remember { mutableStateOf(editing?.symbol ?: initialSymbol) }
    var account by remember { mutableStateOf(editing?.accountId ?: initialAccount ?: state.brokerCash.keys.firstOrNull()) }
    var date by remember { mutableStateOf(editing?.let { LocalDate.parse(it.occurredOn.take(10)) } ?: LocalDate.now()) }
    // A trade entered before times were kept has none, and stays without one unless it is given one.
    var time by remember {
        mutableStateOf(if (editing != null) timeOf(editing.occurredOn) else LocalTime.now().withSecond(0).withNano(0))
    }
    var shares by remember { mutableStateOf(editing?.shares?.toString() ?: "") }
    var price by remember {
        mutableStateOf(
            editing?.let { priceInput(it.price) }
                ?: initialSymbol?.let { state.securitiesBySymbol[it] }?.let { priceInput(it.price) }
                ?: "",
        )
    }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val opened = remember { listOf(kind, symbol, account, date, time, shares, price) }

    val security = symbol?.let { state.securitiesBySymbol[it] }
    val held = state.holdings.firstOrNull { it.symbol == symbol && it.position.accountId == account }
    // What can be sold now: what is held, plus what the sell being edited already takes out of it.
    val available = (held?.shares ?: 0) +
        if (editing != null && editing.kind == TradeKinds.SELL && editing.symbol == symbol && editing.accountId == account) editing.shares else 0
    val sharesValue = parseShares(shares)
    val priceValue = parsePrice(price)
    val occurredOn = date.format(DateTimeFormatter.ISO_LOCAL_DATE) + (time?.let { "T" + it.format(TIME_FORMAT) } ?: "")
    // A back-dated sell, or a buy edited down or moved after a sell that needs it, would sell more than is held.
    val historyHolds = sharesValue == null || priceValue == null || symbol == null || account == null ||
        positions(
            state.trades.filter { it.id != editing?.id } + Trade(
                id = editing?.id ?: "",
                accountId = account!!,
                symbol = symbol!!,
                kind = kind,
                occurredOn = occurredOn,
                shares = sharesValue,
                price = priceValue,
                fees = 0,
                amount = 0,
                transactionId = "",
                createdAt = editing?.createdAt ?: Instant.now().toString(),
            ),
        ) != null

    val form = rememberFormValidation()
    val stockField = form.field("stock", if (security == null) stringResource(R.string.error_choose_stock) else null)
    val accountField = form.field("account", if (state.accounts.none { it.id == account }) stringResource(R.string.error_choose_broker) else null)
    val sharesField = form.field(
        "shares",
        when {
            sharesValue == null -> stringResource(R.string.error_shares_whole)
            kind != TradeKinds.BUY && sharesValue > available -> stringResource(R.string.error_shares_over_held, sharesText(available))
            !historyHolds -> stringResource(R.string.error_shares_over_history)
            else -> null
        },
    )
    val priceField = form.field("price", if (priceValue == null) stringResource(R.string.error_price_number) else null)

    val gross = if (sharesValue != null && priceValue != null) valueOf(sharesValue, priceValue) else null
    val minCommission = Banks.minCommission(state.accounts.firstOrNull { it.id == account }?.bank)
    val fees = gross?.let { if (kind == TradeKinds.DIVIDEND) dividendTax(it) else pseFees(it, kind == TradeKinds.SELL, minCommission).total }

    FullScreenDialog(
        title = stringResource(if (editing != null) R.string.investments_edit_trade else R.string.investments_new_trade),
        onDismiss = onDismiss,
        onSave = submit@{
            val chosenSymbol = symbol ?: return@submit
            val chosenAccount = account ?: return@submit
            val count = sharesValue ?: return@submit
            val each = priceValue ?: return@submit
            busy.run("trade", snackbarHostState, successMessage = savedMessage, onSuccess = onDismiss) {
                onSave(TradeSubmission(chosenAccount, chosenSymbol, kind, occurredOn, count, each, fees ?: 0))
            }
        },
        saveEnabled = form.valid(stockField, accountField, sharesField, priceField),
        submitting = busy.isBusy("trade"),
        dirty = listOf(kind, symbol, account, date, time, shares, price) != opened,
    ) {
        ConnectedButtonGroup(
            options = KINDS,
            selected = kind,
            onSelect = { kind = it },
            label = { tradeKindLabel(it) },
        )

        SecurityField(
            securities = state.securities,
            selected = security,
            field = stockField,
            onSelect = { picked ->
                symbol = picked.symbol
                // The stock fills in the account that already holds it, as a payee fills in the rest.
                state.held.firstOrNull { it.symbol == picked.symbol }?.let { account = it.position.accountId }
                if (kind != TradeKinds.DIVIDEND) price = priceInput(picked.price)
            },
        )

        DropdownField(
            label = stringResource(R.string.investments_label_broker),
            value = state.accounts.firstOrNull { it.id == account }?.name ?: "",
            options = state.accounts.map { SelectOption(it.id, it.name) },
            onSelect = { account = it },
            field = accountField,
            hint = held?.takeIf { it.shares > 0 }?.let { stringResource(R.string.investments_held_hint, sharesText(it.shares)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                value = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                label = stringResource(R.string.label_date),
                onClick = { pickingDate = true },
                modifier = Modifier.weight(1f),
            )
            PickerField(
                value = time?.format(TIME_FORMAT) ?: "",
                label = stringResource(R.string.label_time),
                placeholder = stringResource(R.string.placeholder_optional),
                onClick = { pickingTime = true },
                modifier = Modifier.weight(1f),
            )
        }

        val lot = priceValue?.let { boardLot(it) }
        YuukaTextField(
            value = shares,
            onValueChange = { shares = it },
            label = stringResource(R.string.investments_shares),
            singleLine = true,
            field = sharesField,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            // A dividend is paid on whatever is held, so the board lot means nothing there.
            hint = if (kind == TradeKinds.DIVIDEND || lot == null) null else when {
                sharesValue != null && sharesValue % lot != 0L -> stringResource(R.string.investments_odd_lot_hint, "%,d".format(lot))
                else -> stringResource(R.string.investments_board_lot_hint, "%,d".format(lot))
            },
        )

        YuukaTextField(
            value = price,
            onValueChange = { price = it },
            label = stringResource(if (kind == TradeKinds.DIVIDEND) R.string.investments_label_dividend_per_share else R.string.investments_label_price),
            singleLine = true,
            field = priceField,
            prefix = currencySymbol("PHP"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        if (gross != null && sharesValue != null) Breakdown(kind, gross, held, sharesValue, minCommission)
    }

    if (pickingDate) {
        YuukaDatePickerDialog(initial = date, onDismiss = { pickingDate = false }, onPicked = { date = it; pickingDate = false })
    }
    if (pickingTime) {
        YuukaTimePickerDialog(initial = time ?: LocalTime.now(), onDismiss = { pickingTime = false }, onPicked = { time = it; pickingTime = false })
    }
}

/**
 * The stock, typed rather than scrolled to: the PSE lists hundreds. What is typed is matched against symbols
 * first, then names, and the menu offers the closest few.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityField(securities: List<Security>, selected: Security?, field: FieldState, onSelect: (Security) -> Unit) {
    var query by remember(selected?.symbol) { mutableStateOf(selected?.let { "${it.symbol} · ${it.name}" } ?: "") }
    var open by remember { mutableStateOf(false) }
    val needle = query.trim().uppercase()
    val matches = remember(needle, securities) {
        if (needle.isEmpty() || selected != null && query == "${selected.symbol} · ${selected.name}") {
            emptyList()
        } else {
            (securities.filter { it.symbol.startsWith(needle) } + securities.filter { it.name.uppercase().contains(needle) })
                .distinct()
                .take(8)
        }
    }

    ExposedDropdownMenuBox(expanded = open && matches.isNotEmpty(), onExpandedChange = { open = it }, modifier = Modifier.fillMaxWidth()) {
        YuukaTextField(
            value = query,
            onValueChange = {
                query = it
                open = true
            },
            label = stringResource(R.string.investments_label_stock),
            singleLine = true,
            field = field,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = open && matches.isNotEmpty(), onDismissRequest = { open = false }) {
            matches.forEach { security ->
                DropdownMenuItem(
                    text = { Text("${security.symbol} · ${security.name}", maxLines = 1) },
                    onClick = {
                        onSelect(security)
                        query = "${security.symbol} · ${security.name}"
                        open = false
                    },
                )
            }
        }
    }
}

/** What the trade comes to, fee by fee, ending on the figure that moves the account's cash. */
@Composable
private fun Breakdown(kind: String, gross: Long, held: Holding?, shares: Long, minCommission: Long) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FigureRow(stringResource(R.string.investments_gross)) { MoneyText(gross, style = MaterialTheme.typography.bodyLarge) }
            if (kind == TradeKinds.DIVIDEND) {
                val tax = dividendTax(gross)
                FigureRow(stringResource(R.string.investments_final_tax)) { MoneyText(-tax, style = MaterialTheme.typography.bodyLarge) }
                HorizontalDivider()
                Total(stringResource(R.string.investments_net_dividend), gross - tax)
            } else {
                val sell = kind == TradeKinds.SELL
                val fees = pseFees(gross, sell, minCommission)
                FigureRow(stringResource(R.string.investments_commission)) { MoneyText(fees.commission, style = MaterialTheme.typography.bodyLarge) }
                FigureRow(stringResource(R.string.investments_vat)) { MoneyText(fees.vat, style = MaterialTheme.typography.bodyLarge) }
                FigureRow(stringResource(R.string.investments_pse_fee)) { MoneyText(fees.pseFee, style = MaterialTheme.typography.bodyLarge) }
                FigureRow(stringResource(R.string.investments_sccp)) { MoneyText(fees.sccp, style = MaterialTheme.typography.bodyLarge) }
                if (sell) FigureRow(stringResource(R.string.investments_sales_tax)) { MoneyText(fees.salesTax, style = MaterialTheme.typography.bodyLarge) }
                HorizontalDivider()
                if (sell) {
                    val proceeds = gross - fees.total
                    Total(stringResource(R.string.investments_net_proceeds), proceeds)
                    if (held != null && held.shares > 0 && shares <= held.shares) {
                        val gain = proceeds - Math.round(held.costBasis * shares.toDouble() / held.shares)
                        FigureRow(stringResource(R.string.investments_gain_on_sale)) {
                            MoneyText(gain, tone = MoneyTone.SIGNED_ALERT, explicit = true, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Total(stringResource(R.string.investments_total_cost), gross + fees.total)
                }
            }
        }
    }
}

@Composable
private fun Total(label: String, amount: Long) {
    FigureRow(label) { MoneyText(amount, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)) }
}
