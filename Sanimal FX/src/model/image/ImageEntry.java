package model.image;

import java.awt.image.ImagingOpException;
import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Optional;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import model.SanimalData;
import model.location.Location;
import model.species.Species;
import model.species.SpeciesEntry;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.IImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputField;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.io.FileUtils;

/**
 * A class representing an image file
 * 
 * @author David Slovikosky
 */
public class ImageEntry extends ImageContainer
{
	// The format with which to print the date out in
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYYY MM dd hh mm ss");
	// The icon to use for all images at the moment
	private static final Image DEFAULT_IMAGE_ICON = new Image(ImageEntry.class.getResource("/images/importWindow/imageIcon.png").toString());
	// The icon to use for all tagged images at the moment
	private static final Image CHECKED_IMAGE_ICON = new Image(ImageEntry.class.getResource("/images/importWindow/imageIconDone.png").toString());
	// A property to wrap the currently selected image property. Must not be static!
	private final ObjectProperty<Image> SELECTED_IMAGE_PROPERTY = new SimpleObjectProperty<>(DEFAULT_IMAGE_ICON);
	// The actual file 
	private ObjectProperty<File> imageFileProperty = new SimpleObjectProperty<File>();
	// The date that the image was taken
	private ObjectProperty<Date> dateTakenProperty = new SimpleObjectProperty<Date>();
	// The location that the image was taken
	private ObjectProperty<Location> locationTakenProperty = new SimpleObjectProperty<Location>();
	// The species present in the image
	private ObservableList<SpeciesEntry> speciesPresent = FXCollections.<SpeciesEntry> observableArrayList(image -> new Observable[] {
			image.getAmountProperty(),
			image.getSpeciesProperty()
	});

	/**
	 * Create a new image entry with an image file
	 * 
	 * @param file
	 *            The file (must be an image file)
	 */
	public ImageEntry(File file)
	{
		this.imageFileProperty.setValue(file);
		try
		{
			this.dateTakenProperty.setValue(new Date(Files.readAttributes(file.toPath(), BasicFileAttributes.class).lastModifiedTime().toMillis()));
		}
		catch (IOException e)
		{
		}
		// Bind the image property to a conditional expression.
		// The image is checked if the location is valid and the species present list is not empty
		SELECTED_IMAGE_PROPERTY.bind(Bindings.createObjectBinding(() -> this.getLocationTaken() != null && this.getLocationTaken().locationValid() && !this.getSpeciesPresent().isEmpty() ? CHECKED_IMAGE_ICON : DEFAULT_IMAGE_ICON, this.locationTakenProperty, this.speciesPresent));

		// We create the EXIF data we'll need on the image entry to write to later
		// We do this in a thread since it takes some time to complete...
		SanimalData.getInstance().getTaskPerformer().submit(this::initFileMetadata);
	}

	/**
	 * Getter for the tree icon property
	 *
	 * @return The tree icon to be used
	 */
	@Override
	public ObjectProperty<Image> getTreeIconProperty()
	{
		return SELECTED_IMAGE_PROPERTY;
	}

	/**
	 * Get the image file
	 * 
	 * @return The image file
	 */
	public File getFile()
	{
		return this.imageFileProperty.getValue();
	}

	/**
	 * Set the image file that this image represents
	 * 
	 * @param file
	 *            The file that this class represents
	 */
	public void setFile(File file)
	{
		this.imageFileProperty.setValue(file);
	}

	/**
	 * Get the image file property that this image represents
	 *
	 * @return The file property that this image represents
	 */
	public ObjectProperty<File> getFileProperty()
	{
		return this.imageFileProperty;
	}

	/**
	 * Returns the date taken as a formatted string
	 * 
	 * @return The formatted date
	 */
	public String getDateTakenFormatted()
	{
		//this.validateDate();
		return this.getDateTaken().toString();
	}

	/**
	 * Returns the date the image was taken
	 * 
	 * @return The date the image was taken
	 */
	public Date getDateTaken()
	{
		//this.validateDate();
		return dateTakenProperty.getValue();
	}

	/**
	 * Returns the date property of the image
	 *
	 * @return The date the image was taken property
	 */
	public ObjectProperty<Date> getDateTakenProperty()
	{
		return dateTakenProperty;
	}

	/**
	 * Set the location that the image was taken at
	 * 
	 * @param location
	 *            The location
	 */
	public void setLocationTaken(Location location)
	{
		this.locationTakenProperty.setValue(location);
	}

	/**
	 * Return the location that the image was taken
	 * 
	 * @return The location
	 */
	public Location getLocationTaken()
	{
		return locationTakenProperty.getValue();
	}

	public ObjectProperty<Location> getLocationTakenProperty()
	{
		return locationTakenProperty;
	}

	/**
	 * Add a new species to the image
	 *
	 * @param species
	 *            The species of the animal
	 * @param amount
	 *            The number of animals in the image
	 */
	public void addSpecies(Species species, Integer amount)
	{
		// Grab the old species entry for the given species if present, and then add the amounts
		Optional<SpeciesEntry> currentEntry = this.speciesPresent.stream().filter(speciesEntry -> speciesEntry.getSpecies().equals(species)).findFirst();
		int oldAmount = currentEntry.map(SpeciesEntry::getAmount).orElse(0);
		this.removeSpecies(species);
		this.speciesPresent.add(new SpeciesEntry(species, amount + oldAmount));
	}

	/**
	 * Remove a species from the list of image species
	 * 
	 * @param species
	 *            The species to remove
	 */
	public void removeSpecies(Species species)
	{
		this.speciesPresent.removeIf(entry ->
				entry.getSpecies() == species);
	}

	/**
	 * Get the list of present species
	 * 
	 * @return A list of present species
	 */
	public ObservableList<SpeciesEntry> getSpeciesPresent()
	{
		return speciesPresent;
	}

	/**
	 * Renames the image file based on the formatted date. Unused right now
	 */
	public void renameByDate()
	{
		//this.validateDate();
		String newFilePath = this.getFile().getParentFile() + File.separator;
		String newFileName = DATE_FORMAT.format(this.getDateTaken());
		String newFileExtension = this.getFile().getName().substring(this.getFile().getName().lastIndexOf('.'));
		String newFileCompletePath = newFilePath + newFileName + newFileExtension;
		File newFile = new File(newFileCompletePath);
		int numberOfDuplicateFiles = 0;
		while (newFile.exists())
		{
			newFileCompletePath = newFilePath + newFileName + " (" + numberOfDuplicateFiles++ + ")" + newFileExtension;
			newFile = new File(newFileCompletePath);
		}
		boolean result = this.getFile().renameTo(newFile);
		if (result == false)
			System.err.println("Error renaming file: " + this.getFile().getAbsolutePath());
	}

	//	private void validateDate()
	//	{
	//		if (this.dateTaken == null)
	//		{
	//			try
	//			{
	//				Metadata metadata = ImageMetadataReader.readMetadata(this.imageFile);
	//				FileMetadataDirectory fileMetadata = metadata.<FileMetadataDirectory> getFirstDirectoryOfType(FileMetadataDirectory.class);
	//				if (fileMetadata != null)
	//					dateTaken = fileMetadata.getDate(FileMetadataDirectory.TAG_FILE_MODIFIED_DATE);
	//			}
	//			catch (ImageProcessingException | IOException e)
	//			{
	//				System.out.println("Error reading file metadata: " + this.imageFile.getAbsolutePath() + "\nError is:\n" + e.toString());
	//			}
	//		}
	//	}


	/**
	 * Gets called once to initialize the file with metadata
	 */
	private void initFileMetadata()
	{
		try
		{
			IImageMetadata metadata = Imaging.getMetadata(this.getFile());

			TiffOutputSet outputSet = null;
			if (metadata instanceof JpegImageMetadata)
			{
				JpegImageMetadata jpegImageMetadata = (JpegImageMetadata) metadata;
				TiffImageMetadata tiffImageMetadata = jpegImageMetadata.getExif();

				if (tiffImageMetadata != null)
					outputSet = tiffImageMetadata.getOutputSet();
			}
			if (outputSet == null)
				outputSet = new TiffOutputSet();


			if (this.getLocationTaken() != null)
				outputSet.setGPSInDegrees(this.getLocationTaken().getLng(), this.getLocationTaken().getLat());

			TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
			exifDirectory.removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT);
			exifDirectory.add(ExifTagConstants.EXIF_TAG_USER_COMMENT, "SANIMAL");

			File tempToWriteTo = File.createTempFile("sanimalTMP", ".jpg");
			OutputStream outputStream = new FileOutputStream(tempToWriteTo);
			new ExifRewriter().updateExifMetadataLossless(this.getFile(), outputStream, outputSet);
			outputStream.close();
			this.getFile().delete();
			FileUtils.copyFile(tempToWriteTo, this.getFile());
			tempToWriteTo.delete();

			System.out.println(metadata);
		}
		catch (ImageReadException | IOException | ImageWriteException e)
		{
			System.err.println("Exception occurred when trying to read the metadata from the file: " + this.getFile().getAbsolutePath());
			System.err.println("The error was: ");
			e.printStackTrace();
		}
	}
}
