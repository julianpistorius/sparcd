package controller;

import com.panemu.tiwulfx.control.DetachableTabPane;
import controller.analysisView.VisCSVController;
import controller.analysisView.VisDrSandersonController;
import controller.analysisView.VisSpeciesAccumulationCurveController;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import model.SanimalData;
import model.analysis.DataAnalysis;
import model.image.CloudImageEntry;
import model.image.ImageEntry;
import model.location.Location;
import model.species.Species;
import model.species.SpeciesEntry;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller class for the analysis page
 */
public class SanimalAnalysisController implements Initializable
{
	///
	/// FXML bound fields start
	///

	@FXML
	public ListView<Species> speciesFilterListView;

	@FXML
	public TextField txtSpeciesSearch;

	@FXML
	public ListView<Location> locationFilterListView;

	@FXML
	public TextField txtLocationSearch;

	@FXML
	public TextField txtEventInterval;

	@FXML
	public VisDrSandersonController visDrSandersonController;
	@FXML
	public VisSpeciesAccumulationCurveController visSpeciesAccumulationCurveController;
	@FXML
	public VisCSVController visCSVController;

	@FXML
	public DatePicker dateStart;
	@FXML
	public DatePicker dateEnd;

	@FXML
	public DetachableTabPane tbnVisualizations;

	///
	/// FXML bound fields end
	///

	/**
	 * Initialize sets up the analysis window and bindings
	 *
	 * @param location ignored
	 * @param resources ignored
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources)
	{
		// First we setup the species list

		// Grab the global species list
		SortedList<Species> species = new SortedList<>(SanimalData.getInstance().getSpeciesList());
		// We set the comparator to be the name of the species
		species.setComparator(Comparator.comparing(Species::getName));
		// We create a local wrapper of the species list to filter
		FilteredList<Species> speciesFilteredList = new FilteredList<>(species);
		// Set the filter to update whenever the species search text changes
		this.txtSpeciesSearch.textProperty().addListener(observable -> {
			speciesFilteredList.setPredicate(speciesToFilter ->
					// Allow any species with a name or scientific name containing the species search text
					(StringUtils.containsIgnoreCase(speciesToFilter.getName(), this.txtSpeciesSearch.getCharacters()) ||
							StringUtils.containsIgnoreCase(speciesToFilter.getScientificName(), this.txtSpeciesSearch.getCharacters())));
		});
		// Set the items of the species list view to the newly sorted list
		this.speciesFilterListView.setItems(speciesFilteredList);
		this.speciesFilterListView.setCellFactory(CheckBoxListCell.forListView(Species::shouldBePartOfAnalysisProperty));
		this.speciesFilterListView.setEditable(true);

		// Next we setup the location list

		// Grab the global location list
		SortedList<Location> locations = new SortedList<>(SanimalData.getInstance().getLocationList());
		// We set the comparator to be the name of the location
		locations.setComparator(Comparator.comparing(Location::getName));
		// We create a local wrapper of the location list to filter
		FilteredList<Location> locationsFilteredList = new FilteredList<>(locations);
		// Set the filter to update whenever the location search text changes
		this.txtLocationSearch.textProperty().addListener(observable -> {
			locationsFilteredList.setPredicate(locationToFilter ->
					// Allow any location with a name or id containing the location search text
					(StringUtils.containsIgnoreCase(locationToFilter.getName(), this.txtLocationSearch.getCharacters()) ||
							StringUtils.containsIgnoreCase(locationToFilter.getId(), this.txtLocationSearch.getCharacters())));
		});
		// Set the items of the location list view to the newly sorted list
		this.locationFilterListView.setItems(locationsFilteredList);
		this.locationFilterListView.setCellFactory(CheckBoxListCell.forListView(Location::shouldBePartOfAnalysisProperty));
		this.locationFilterListView.setEditable(true);

		/*
		ObjectProperty<LocalDate> dateStartProperty = new SimpleObjectProperty<>(LocalDate.now());
		dateStartProperty.bind(Bindings.createObjectBinding(() -> LocalDate.from(Instant.ofEpochMilli(SanimalData.getInstance().getAllImages().stream().min(Comparator.comparing(ImageEntry::getDateTaken)).get().getDateTaken().getTime())), SanimalData.getInstance().getImageTree().getChildren()));
		ObjectProperty<LocalDate> dateEndProperty = new SimpleObjectProperty<>(LocalDate.now());
		dateEndProperty.bind(Bindings.createObjectBinding(() -> LocalDate.from(Instant.ofEpochMilli(SanimalData.getInstance().getAllImages().stream().max(Comparator.comparing(ImageEntry::getDateTaken)).get().getDateTaken().getTime())), SanimalData.getInstance().getImageTree().getChildren()));

		this.dateStart.setDayCellFactory(x -> new DateCell()
		{
			@Override
			public void updateItem(LocalDate item, boolean empty)
			{
				super.updateItem(item, empty);
				if (item.isBefore(dateStartProperty.getValue()) || item.isAfter(dateEndProperty.getValue()))
				{
					setDisable(true);
				}
			}
		});
		*/
	}

	public void refreshVisualizations(ActionEvent actionEvent)
	{
		// First parse all parameters, starting with event interval (default 30 min)
		Integer eventInterval = 30;
		try
		{
			eventInterval = Integer.parseInt(this.txtEventInterval.getText());
		}
		catch (NumberFormatException ignored) {}

		if (eventInterval <= 0)
			eventInterval = 30;

		LocalDate startDate = this.dateStart.getValue() == null ? LocalDate.MAX : this.dateStart.getValue();
		LocalDate endDate = this.dateEnd.getValue() == null ? LocalDate.MIN : this.dateEnd.getValue();

		// Now process the filters
		List<ImageEntry> imagesToAnalyze = SanimalData.getInstance().getAllImages().stream()
				// Cloud images not allowed
				.filter(imageEntry -> !(imageEntry instanceof CloudImageEntry))
				// Test for checked location
				.filter(imageEntry -> imageEntry.getLocationTaken().shouldBePartOfAnalysis())
				// Test for checked species
				.filter(imageEntry -> imageEntry.getSpeciesPresent().stream().map(SpeciesEntry::getSpecies).anyMatch(Species::shouldBePartOfAnalysis))
				// Test for the date range
				.filter(imageEntry -> imageEntry.getDateTaken().isAfter(startDate.atStartOfDay()) && imageEntry.getDateTaken().isBefore(endDate.atStartOfDay()))
				.collect(Collectors.toList());

		DataAnalysis dataStatistics = new DataAnalysis(imagesToAnalyze, eventInterval);

		visDrSandersonController.visualize(dataStatistics);
		visSpeciesAccumulationCurveController.visualize(dataStatistics);
		visCSVController.visualize(dataStatistics);
	}

	public void selectAllSpecies(ActionEvent actionEvent)
	{
		SanimalData.getInstance().getSpeciesList().forEach(species -> species.setShouldBePartOfAnalysis(true));
	}

	public void selectNoSpecies(ActionEvent actionEvent)
	{
		SanimalData.getInstance().getSpeciesList().forEach(species -> species.setShouldBePartOfAnalysis(false));
	}

	public void clearSpeciesSearch(ActionEvent actionEvent)
	{
		this.txtSpeciesSearch.clear();
	}

	public void selectAllLocations(ActionEvent actionEvent)
	{
		SanimalData.getInstance().getLocationList().forEach(location -> location.setShouldBePartOfAnalysis(true));
	}

	public void selectNoLocations(ActionEvent actionEvent)
	{
		SanimalData.getInstance().getLocationList().forEach(location -> location.setShouldBePartOfAnalysis(false));
	}

	public void clearLocationSearch(ActionEvent actionEvent)
	{
		this.txtLocationSearch.clear();
	}
}
